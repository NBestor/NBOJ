export const YINGQI_SIGNATURE = {
  owner: "yingqi",
  product: "Wenzhong AI Coding Learning Platform",
  claim: "yingqi|wenzhong-ai-learning-platform|nboj|2026-05-19",
  fingerprint: "00f40662ae433dacddf0157fca60a279bf71a54fbf04ee7d50d3190752554b5d",
  headers: {
    owner: "X-Project-Owner",
    signature: "X-Yingqi-Signature",
    claim: "X-Yingqi-Claim"
  }
} as const;

declare global {
  interface Window {
    __YINGQI_SIGNATURE__?: typeof YINGQI_SIGNATURE;
  }
}

function setMeta(name: string, content: string) {
  const selector = `meta[name="${name}"]`;
  let meta = document.head.querySelector<HTMLMetaElement>(selector);
  if (!meta) {
    meta = document.createElement("meta");
    meta.name = name;
    document.head.appendChild(meta);
  }
  meta.content = content;
}

export function applyYingqiRuntimeSignature() {
  window.__YINGQI_SIGNATURE__ = YINGQI_SIGNATURE;
  document.documentElement.dataset.projectOwner = YINGQI_SIGNATURE.owner;
  document.documentElement.dataset.yingqiSignature = YINGQI_SIGNATURE.fingerprint;
  setMeta("author", YINGQI_SIGNATURE.owner);
  setMeta("x-yingqi-signature", YINGQI_SIGNATURE.fingerprint);
  setMeta("x-yingqi-claim", YINGQI_SIGNATURE.claim);
}
