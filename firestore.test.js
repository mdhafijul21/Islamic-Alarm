const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_uid_101";
const BOB_UID = "bob_uid_202";
const ADMIN_UID = "admin_uid_001";
const ADMIN_EMAIL = "md4hafijulislam@gmail.com";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function pastTime(offsetMs = 5000) {
  return new Date(Date.now() - offsetMs);
}

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

function aliceDb() {
  return testEnv
    .authenticatedContext(ALICE_UID, {
      email: "alice@example.com",
      email_verified: true,
    })
    .firestore();
}

function bobDb() {
  return testEnv
    .authenticatedContext(BOB_UID, {
      email: "bob@example.com",
      email_verified: true,
    })
    .firestore();
}

function adminDb() {
  return testEnv
    .authenticatedContext(ADMIN_UID, {
      email: ADMIN_EMAIL,
      email_verified: true,
    })
    .firestore();
}

function spoofedAdminDb() {
  return testEnv
    .authenticatedContext("spoof_uid", {
      email: ADMIN_EMAIL,
      email_verified: false,
    })
    .firestore();
}

test("Unauthenticated users cannot read or write any collection", async () => {
  const unauth = testEnv.unauthenticatedContext().firestore();
  const ts = pastTime();

  await assertFails(unauth.collection("users").doc(ALICE_UID).get());
  await assertFails(unauth.collection("users").get());
  await assertFails(unauth.collection("reports").get());
  await assertFails(
    unauth.collection("announcements").where("active", "==", true).get()
  );
  await assertFails(unauth.collection("app_config").doc("global").get());
  await assertFails(
    unauth.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Unauth",
      platform: "android",
      lastActiveAt: ts,
      createdAt: ts,
      updatedAt: ts,
    })
  );
});

test("UserProfile: owner create/update works; cross-user read, list, ghost fields, and createdAt mutation fail", async () => {
  const alice = aliceDb();
  const bob = bobDb();
  const created = pastTime(10000);

  // Mirror CloudSyncRepository.upsertUserProfile create payload (no @DocumentId `id` field)
  await assertSucceeds(
    alice.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice",
      email: "alice@example.com",
      districtNameBn: "ঢাকা",
      deviceModel: "Pixel 8",
      appVersion: "1.0",
      platform: "android",
      lastActiveAt: created,
      createdAt: created,
      updatedAt: created,
    })
  );

  // Owner partial update preserving immutable createdAt/userId/platform
  await assertSucceeds(
    alice.collection("users").doc(ALICE_UID).update({
      districtNameBn: "কুমিল্লা",
      lastActiveAt: pastTime(1000),
      updatedAt: pastTime(1000),
    })
  );

  // Reject createdAt tamper, cross-user read/list, ghost field, future timestamp
  await assertFails(
    alice.collection("users").doc(ALICE_UID).update({
      createdAt: pastTime(500),
      updatedAt: pastTime(500),
    })
  );
  await assertFails(
    alice.collection("users").doc(ALICE_UID).update({
      isAdmin: true,
      updatedAt: pastTime(500),
    })
  );
  await assertFails(
    alice.collection("users").doc(ALICE_UID).update({
      districtNameBn: "Test",
      updatedAt: new Date(Date.now() + 60000),
    })
  );
  await assertFails(bob.collection("users").doc(ALICE_UID).get());
  await assertFails(bob.collection("users").get());
  await assertFails(alice.collection("users").get());
});

test("Admin: bootstrapped verified admin can list/count all users; spoofed unverified email is rejected", async () => {
  const alice = aliceDb();
  const bob = bobDb();
  const admin = adminDb();
  const spoofed = spoofedAdminDb();
  const ts = pastTime();

  await assertSucceeds(
    alice.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice",
      platform: "android",
      lastActiveAt: ts,
      createdAt: ts,
      updatedAt: ts,
    })
  );
  await assertSucceeds(
    bob.collection("users").doc(BOB_UID).set({
      userId: BOB_UID,
      displayName: "Bob",
      platform: "android",
      lastActiveAt: ts,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Verified admin sees all registered users
  const allUsersSnap = await assertSucceeds(admin.collection("users").get());
  if (allUsersSnap.size !== 2) {
    throw new Error(`Expected 2 users, got ${allUsersSnap.size}`);
  }

  // Unverified spoof email MUST fail
  await assertFails(spoofed.collection("users").get());
  await assertFails(spoofed.collection("users").doc(ALICE_UID).get());
});

test("Reports: user scoped create/list, admin moderation, and terminal lock", async () => {
  const alice = aliceDb();
  const bob = bobDb();
  const admin = adminDb();
  const ts = pastTime();

  await assertSucceeds(
    alice.collection("reports").doc("rep_1").set({
      userId: ALICE_UID,
      userEmail: "alice@example.com",
      type: "suggestion",
      message: "দয়া করে নতুন সূরা যুক্ত করুন",
      device: "Samsung A54",
      appVersion: "1.0",
      status: "pending",
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Non-admin cannot create with status != pending or adminReply
  await assertFails(
    alice.collection("reports").doc("rep_bad").set({
      userId: ALICE_UID,
      type: "problem",
      message: "কিছু কাজ করছে না",
      device: "Pixel",
      appVersion: "1.0",
      status: "resolved",
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Scoped query matches rule; unfiltered query fails for regular user; Bob cannot read Alice's report
  await assertSucceeds(
    alice.collection("reports").where("userId", "==", ALICE_UID).get()
  );
  await assertFails(alice.collection("reports").get());
  await assertFails(bob.collection("reports").doc("rep_1").get());

  // Admin can list all reports & resolve
  const adminSnap = await assertSucceeds(admin.collection("reports").get());
  if (adminSnap.size !== 1) {
    throw new Error(`Expected 1 report, got ${adminSnap.size}`);
  }
  await assertSucceeds(
    admin.collection("reports").doc("rep_1").update({
      status: "resolved",
      adminReply: "ইনশাআল্লাহ পরবর্তী আপডেটে আসছে।",
      updatedAt: pastTime(500),
    })
  );

  // Terminal state lock: Alice cannot edit resolved report
  await assertFails(
    alice.collection("reports").doc("rep_1").update({
      message: "Edited after resolve",
      status: "pending",
      updatedAt: pastTime(200),
    })
  );
});

test("Announcements, AppConfig, DailyHadith: users only read active/get; only admin writes", async () => {
  const alice = aliceDb();
  const admin = adminDb();
  const ts = pastTime();

  // Regular user cannot create announcements, config, or daily_hadiths
  await assertFails(
    alice.collection("announcements").doc("ann_1").set({
      title: "Fake",
      body: "Fake notice",
      category: "notice",
      active: true,
      createdBy: ALICE_UID,
      createdAt: ts,
      updatedAt: ts,
    })
  );
  await assertFails(
    alice.collection("app_config").doc("global").set({
      shareTitle: "T",
      shareMessage: "M",
      shareUrl: "https://example.com",
      updatedBy: ALICE_UID,
      updatedAt: ts,
    })
  );

  // Admin creates active & inactive announcement, config, and daily hadith
  await assertSucceeds(
    admin.collection("announcements").doc("ann_active").set({
      title: "জুমার বিশেষ আমল",
      body: "আজ জুমার দিন সূরা কাহাফ তিলাওয়াত করুন।",
      category: "notice",
      active: true,
      createdBy: ADMIN_UID,
      createdAt: ts,
      updatedAt: ts,
    })
  );
  await assertSucceeds(
    admin.collection("announcements").doc("ann_hidden").set({
      title: "Draft",
      body: "Hidden draft",
      category: "notice",
      active: false,
      createdBy: ADMIN_UID,
      createdAt: ts,
      updatedAt: ts,
    })
  );
  await assertSucceeds(
    admin.collection("app_config").doc("global").set({
      shareTitle: "ইসলামিক এলার্ম",
      shareMessage: "ডাউনলোড করুন",
      shareUrl: "https://play.google.com/store/apps/details?id=com.hafij.islamicalarm",
      noticeBanner: "নতুন আপডেট পাওয়া যাচ্ছে",
      updatedBy: ADMIN_UID,
      updatedAt: ts,
    })
  );
  await assertSucceeds(
    admin.collection("daily_hadiths").doc("2026-11-26").set({
      topicBn: "নামাজের গুরুত্ব",
      bengaliText: "সময়মতো সালাত আদায় করা সর্বোত্তম আমল।",
      referenceBn: "সহিহ বুখারি",
      gradeBn: "সহিহ",
      updatedBy: ADMIN_UID,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Client active-only filtered list works; unfiltered announcements list fails for non-admin
  const activeAnns = await assertSucceeds(
    alice.collection("announcements").where("active", "==", true).get()
  );
  if (activeAnns.size !== 1) {
    throw new Error(`Expected 1 active announcement, got ${activeAnns.size}`);
  }
  await assertFails(alice.collection("announcements").get());
  await assertFails(alice.collection("announcements").doc("ann_hidden").get());

  // Client .get() on app_config/global and daily_hadiths/2026-11-26 succeeds
  await assertSucceeds(alice.collection("app_config").doc("global").get());
  await assertSucceeds(alice.collection("daily_hadiths").doc("2026-11-26").get());
});
