import { createHash, createHmac, randomInt, timingSafeEqual } from "node:crypto";
import { initializeApp } from "firebase-admin/app";
import { getAuth } from "firebase-admin/auth";
import { FieldValue, Timestamp, getFirestore } from "firebase-admin/firestore";
import { defineSecret } from "firebase-functions/params";
import { HttpsError, onCall } from "firebase-functions/v2/https";

initializeApp();
const db = getFirestore();
const mailKey = defineSecret("RESEND_API_KEY");
const hmacKey = defineSecret("OTP_HMAC_SECRET");
const fromEmail = defineSecret("OTP_FROM_EMAIL");
const REGION = "asia-south1";
const TTL = 10 * 60 * 1000;
const RESEND_DELAY = 60 * 1000;
const MAX_ATTEMPTS = 5;

function normalizeEmail(value: unknown): string {
  if (typeof value !== "string") throw new HttpsError("invalid-argument", "Enter a valid email.");
  const email = value.trim().toLowerCase();
  if (email.length > 254 || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) throw new HttpsError("invalid-argument", "Enter a valid email.");
  return email;
}
const keyFor = (email: string) => createHash("sha256").update(email).digest("hex");
const digest = (email: string, code: string) => createHmac("sha256", hmacKey.value()).update(email + ":" + code).digest("hex");

export const requestEmailOtp = onCall({region:REGION,secrets:[mailKey,hmacKey,fromEmail],enforceAppCheck:true}, async (request) => {
  const email = normalizeEmail(request.data?.email);
  const ref = db.collection("novaEmailOtps").doc(keyFor(email));
  const now = Date.now();
  const code = randomInt(0, 1000000).toString().padStart(6, "0");
  await db.runTransaction(async tx => {
    const snap = await tx.get(ref);
    const data = snap.data();
    if (data?.resendAfter && data.resendAfter.toMillis() > now) throw new HttpsError("resource-exhausted", "Wait before requesting another code.");
    const count = data?.sentAt && data.sentAt.toMillis() > now - 3600000 ? (data.hourCount ?? 0) : 0;
    if (count >= 5) throw new HttpsError("resource-exhausted", "Too many codes requested. Try again later.");
    tx.set(ref,{digest:digest(email,code),expiresAt:Timestamp.fromMillis(now+TTL),resendAfter:Timestamp.fromMillis(now+RESEND_DELAY),attempts:0,sentAt:Timestamp.fromMillis(now),hourCount:count+1});
  });
  const sent = await fetch("https://api.resend.com/emails",{method:"POST",headers:{Authorization:"Bearer "+mailKey.value(),"Content-Type":"application/json"},body:JSON.stringify({from:fromEmail.value(),to:[email],subject:"Your Nova Browser sign-in code",text:"Your Nova Browser sign-in code is "+code+". It expires in 10 minutes. If you did not request this, ignore this email."})});
  if (!sent.ok) { await ref.delete(); console.error("Resend delivery failed",sent.status); throw new HttpsError("unavailable","Email delivery is unavailable. Try again later."); }
  return {ok:true,message:"If this address is eligible, a sign-in code will arrive shortly."};
});

export const verifyEmailOtp = onCall({region:REGION,secrets:[hmacKey],enforceAppCheck:true},async request => {
  const email = normalizeEmail(request.data?.email);
  const code = typeof request.data?.code === "string" ? request.data.code.trim() : "";
  if (!/^\d{6}$/.test(code)) throw new HttpsError("invalid-argument","Enter the 6-digit code.");
  const ref = db.collection("novaEmailOtps").doc(keyFor(email));
  const snap = await ref.get();
  const data = snap.data();
  if (!data || !data.expiresAt || data.expiresAt.toMillis() <= Date.now()) throw new HttpsError("deadline-exceeded","Code expired. Request a new one.");
  if ((data.attempts ?? 0) >= MAX_ATTEMPTS) { await ref.delete(); throw new HttpsError("permission-denied","Too many attempts. Request a new code."); }
  const expected = Buffer.from(data.digest,"hex");
  const actual = Buffer.from(digest(email,code),"hex");
  if (expected.length !== actual.length || !timingSafeEqual(expected,actual)) {
    await ref.update({attempts:FieldValue.increment(1)});
    throw new HttpsError("permission-denied","Incorrect code.");
  }
  await ref.delete();
  let user;
  try {
    user = await getAuth().getUserByEmail(email);
    if (!user.emailVerified) user = await getAuth().updateUser(user.uid,{emailVerified:true});
  } catch (error: any) {
    if (error?.code !== "auth/user-not-found") throw error;
    user = await getAuth().createUser({email,emailVerified:true});
  }
  return {token:await getAuth().createCustomToken(user.uid)};
});