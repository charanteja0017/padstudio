# Preview assets (plan §8)

These scripts rebuild `docs/redesign/assets/{icons,manifest.json,before}` from the owner's home-screen screenshots:

```
python3 -I -B tools/lib/redesign_assets/detect.py …        # icon boxes → det*.json
python3 -I -B tools/lib/redesign_assets/make_assets.py detA.json detB.json --write
python3 -I -B tools/lib/redesign_assets/verify.py
```

They read `docs/device/raw/20261011-162623/` (the launcher's own record of which app sits in which cell), which is git-ignored because it is personal device data. So the assets can be regenerated on this Mac, not from a fresh clone. Scratch output goes to `$PAD_ASSET_SCRATCH` (default: the system temp folder).
