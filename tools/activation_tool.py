"""Offline owner activation (run on the OWNER's own computer; nothing here is secret except issuer_private.pem, which must NEVER be committed or sent anywhere).
  python3 tools/activation_tool.py genkey   -> writes issuer_private.pem (keep offline!) and app/src/main/assets/issuer_public_key.b64 (public; commit this)
  python3 tools/activation_tool.py issue "<PSREQ1|...request text copied from the app's About screen>"  -> prints the activation token to paste into the app
Needs: pip install cryptography"""
import base64, datetime, sys
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import ec

PUB = "app/src/main/assets/issuer_public_key.b64"
if sys.argv[1:2] == ["genkey"]:
    k = ec.generate_private_key(ec.SECP256R1())
    open("issuer_private.pem", "wb").write(k.private_bytes(serialization.Encoding.PEM, serialization.PrivateFormat.PKCS8, serialization.NoEncryption()))
    open(PUB, "w").write(base64.b64encode(k.public_key().public_bytes(serialization.Encoding.DER, serialization.PublicFormat.SubjectPublicKeyInfo)).decode())
    print("issuer_private.pem written (keep OFFLINE, never commit). Public key written to", PUB)
elif sys.argv[1:2] == ["issue"]:
    _, owner, pkg, dev = sys.argv[2].strip().split("|")[0:4]
    k = serialization.load_pem_private_key(open("issuer_private.pem", "rb").read(), None)
    when = datetime.date.today().isoformat()
    text = f"PSACT1|{owner}|{pkg}|{dev}|{when}"
    sig = base64.b64encode(k.sign(text.encode(), ec.ECDSA(hashes.SHA256()))).decode()
    print(text + "|" + sig)
else:
    print(__doc__)
