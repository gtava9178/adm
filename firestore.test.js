const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";
const CHARLIE_UID = "charlie_789";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated user cannot read or write any collection", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("directory").where("discoverable", "==", true).get());
  await assertFails(unauthDb.collection("chats").where("memberUids", "array-contains", ALICE_UID).get());
  await assertFails(unauthDb.collection("stories").where("discoverable", "==", true).get());
});

test("Private user PII and settings are strictly owner-isolated", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const ts = new Date(Date.now() - 2000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      email: "alice@example.com",
      phone: "+966500000001",
      themeMode: "dark",
      chatWallpaper: "emerald_classic",
      createdAt: ts,
      updatedAt: ts,
    })
  );

  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).update({
      themeMode: "light",
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Reject ghost field & future timestamp
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      isAdmin: true,
      updatedAt: new Date(Date.now() - 500),
    })
  );
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      themeMode: "light",
      updatedAt: new Date(Date.now() + 60000),
    })
  );
});

test("Directory profiles allow discoverable list query and owner-only writes", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const ts = new Date(Date.now() - 2000);

  await assertSucceeds(
    aliceDb.collection("directory").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "أليس",
      username: "alice_sa",
      bio: "متاح الآن",
      online: true,
      discoverable: true,
      hideLastSeen: false,
      hideReadReceipts: false,
      lastSeen: ts,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Matches Kotlin repository query: .whereEqualTo("discoverable", true)
  await assertSucceeds(bobDb.collection("directory").where("discoverable", "==", true).get());
  // Unfiltered list query must fail
  await assertFails(bobDb.collection("directory").get());
  // Cross-user profile overwrite must fail
  await assertFails(
    bobDb.collection("directory").doc(ALICE_UID).update({
      displayName: "Hacked",
      updatedAt: new Date(Date.now() - 500),
    })
  );
});

test("Chats, messages, and typing enforce memberUids isolation and parity", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const charlieDb = testEnv.authenticatedContext(CHARLIE_UID).firestore();
  const ts = new Date(Date.now() - 5000);
  const chatId = "dm_alice_bob";

  await assertSucceeds(
    aliceDb.collection("chats").doc(chatId).set({
      type: "direct",
      title: "",
      memberUids: [ALICE_UID, BOB_UID],
      adminUids: [ALICE_UID, BOB_UID],
      createdBy: ALICE_UID,
      lastMessageText: "",
      lastMessageType: "none",
      lastMessageSenderId: ALICE_UID,
      lastMessageStatus: "none",
      unreadCounts: {},
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Query alignment: whereArrayContains("memberUids", uid)
  await assertSucceeds(bobDb.collection("chats").where("memberUids", "array-contains", BOB_UID).get());
  await assertFails(charlieDb.collection("chats").where("memberUids", "array-contains", BOB_UID).get());
  await assertFails(charlieDb.collection("chats").doc(chatId).get());

  // Send message (NO @DocumentId 'id' field)
  await assertSucceeds(
    aliceDb.collection("chats").doc(chatId).collection("messages").doc("msg_1").set({
      chatId: chatId,
      senderId: ALICE_UID,
      senderName: "أليس",
      memberUids: [ALICE_UID, BOB_UID],
      type: "text",
      text: "السلام عليكم",
      status: "sent",
      readBy: [ALICE_UID],
      deleted: false,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Reject message with redundant 'id' key
  await assertFails(
    aliceDb.collection("chats").doc(chatId).collection("messages").doc("msg_2").set({
      id: "msg_2",
      chatId: chatId,
      senderId: ALICE_UID,
      senderName: "أليس",
      memberUids: [ALICE_UID, BOB_UID],
      type: "text",
      text: "bad",
      status: "sent",
      readBy: [ALICE_UID],
      deleted: false,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Recipient Bob can mark message read, but cannot edit text
  await assertSucceeds(
    bobDb.collection("chats").doc(chatId).collection("messages").doc("msg_1").update({
      status: "read",
      readBy: [ALICE_UID, BOB_UID],
      updatedAt: new Date(Date.now() - 500),
    })
  );
  await assertFails(
    bobDb.collection("chats").doc(chatId).collection("messages").doc("msg_1").update({
      text: "tampered",
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Non-member Charlie cannot read messages (unfiltered, cross-user filtered, or direct get) or post typing indicator
  await assertFails(
    charlieDb.collection("chats").doc(chatId).collection("messages").get()
  );
  await assertFails(
    charlieDb.collection("chats").doc(chatId).collection("messages").where("memberUids", "array-contains", ALICE_UID).get()
  );
  await assertFails(
    charlieDb.collection("chats").doc(chatId).collection("messages").doc("msg_1").get()
  );
  await assertFails(
    charlieDb.collection("chats").doc(chatId).collection("typing").doc(CHARLIE_UID).set({
      userId: CHARLIE_UID,
      displayName: "Charlie",
      typing: true,
      memberUids: [ALICE_UID, BOB_UID, CHARLIE_UID],
      updatedAt: ts,
    })
  );

  // Alice can write typing status
  await assertSucceeds(
    aliceDb.collection("chats").doc(chatId).collection("typing").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "أليس",
      typing: true,
      memberUids: [ALICE_UID, BOB_UID],
      updatedAt: ts,
    })
  );
});

test("Stories allow owner create/delete and signed-in viewer updates", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const ts = new Date(Date.now() - 3000);

  await assertSucceeds(
    aliceDb.collection("stories").doc("story_1").set({
      userId: ALICE_UID,
      authorName: "أليس",
      type: "text",
      text: "صباح الخير جميعاً",
      backgroundColorHex: 0xFF0D6E5C,
      discoverable: true,
      viewerUids: [],
      expiresAtEpochMs: Date.now() + 86400000,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  await assertSucceeds(bobDb.collection("stories").where("discoverable", "==", true).get());
  await assertSucceeds(
    bobDb.collection("stories").doc("story_1").update({
      viewerUids: [BOB_UID],
      updatedAt: new Date(Date.now() - 500),
    })
  );
  // Bob cannot edit Alice's story text or delete Alice's story
  await assertFails(
    bobDb.collection("stories").doc("story_1").update({
      text: "Hijacked",
      updatedAt: new Date(Date.now() - 500),
    })
  );
  await assertFails(bobDb.collection("stories").doc("story_1").delete());
  await assertSucceeds(aliceDb.collection("stories").doc("story_1").delete());
});
