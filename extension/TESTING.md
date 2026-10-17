# Testing the PriceWatch extension

Thanks for trying the PriceWatch extension before its release. The test build talks to a copy of PriceWatch on
your own computer, so you run the app first and then add the extension to Chrome. This takes about 15 minutes
the first time.

## 1. What you need

| Tool | Version |
| --- | --- |
| Google Chrome | recent |
| Java (JDK) | 21 or newer, e.g. [Temurin](https://adoptium.net). On macOS or Linux, `backend/setup.sh` can download it for you. |
| Node.js | 20.19+ or 22.12+ ([nodejs.org](https://nodejs.org)) |
| Git | any |

You do not need an account or any keys. Without them PriceWatch runs in guest mode, which is what the test
build uses.

## 2. Start PriceWatch

Use the source code of the version you are testing, so the app and the extension match:

```bash
git clone https://github.com/NHasan143/price-watch.git
cd price-watch
git checkout extension-v0.1.0      # the version on the release page
```

Then start the backend and the web app in two terminals.

```bash
# Terminal 1: the backend, on http://localhost:8080
cd backend
./setup.sh && source activate      # macOS / Linux: downloads Java 21 and Maven into backend/.tools
mvn spring-boot:run                # wait for "Started PriceWatchApplication"
```

Already have Java 21 and Maven installed (or on Windows)? Skip the `setup.sh` line.

```bash
# Terminal 2: the web app, on http://localhost:5173
cd frontend
npm install
npm run dev
```

Open <http://localhost:5173>. You should see an empty shelf.

## 3. Add the extension to Chrome

1. Download `pricewatch-extension-<version>.zip` from the
   [release page](https://github.com/NHasan143/price-watch/releases) and unzip it.
2. Open `chrome://extensions` and turn on **Developer mode** (top right).
3. Click **Load unpacked** and choose the unzipped folder (the one containing `manifest.json`).
4. Click the puzzle-piece icon in Chrome's toolbar and pin **PriceWatch**.

Chrome may show a "Disable developer mode extensions" notice on start. That is normal for test builds;
click **Cancel** to keep it.

## 4. What to try

Open the web app at <http://localhost:5173> once first, so the extension and the web app share the same guest
shelf. Then:

- [ ] Open a product page in a shop you use and click the PriceWatch icon. Are the name, photo and price right?
- [ ] Pick a target (or keep the suggested one) and add it. Does it appear on the shelf at localhost:5173?
- [ ] Click the icon again on the same product. Does it show your saved target?
- [ ] Try a page that is not a product (a search result, a news site). Is the message clear?
- [ ] Stop the backend and click the icon. Does it say PriceWatch is not running?
- [ ] Try shops from different countries and currencies.

As a guest, a product is read once and not re-checked, and you can add up to 10. That is expected in this test.

## 5. Tell us what you found

[Open a feedback issue](https://github.com/NHasan143/price-watch/issues/new?template=extension-feedback.yml).
For a shop it reads wrongly, the page link and a screenshot of the popup are the most useful.

## Updating to a new test build

Download the new zip, unzip it over the old folder (or into a new one), and click the reload icon on the
PriceWatch card in `chrome://extensions` (or **Load unpacked** again for a new folder). Check out the matching
tag (`git checkout extension-v<version>`) and restart the backend and web app.

## Testing with accounts

The test build is guest-only. To try sign-in and email alerts, set up Clerk and build the extension from source
as described in the [README](../README.md#chrome-extension).
