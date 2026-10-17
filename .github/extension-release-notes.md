PriceWatch for Chrome adds the product page you are looking at to your PriceWatch shelf in one click.

> [!IMPORTANT]
> This is a test build. It works with a copy of PriceWatch running on your own computer
> (`localhost:8080` and `localhost:5173`). It is not on the Chrome Web Store yet.

## What's new

{{CHANGES}}

## Install

1. Start PriceWatch on your computer from this release's source code:
   `git clone https://github.com/{{REPO}}.git && cd price-watch && git checkout extension-v{{VERSION}}`,
   then follow [Testing the extension](https://github.com/{{REPO}}/blob/extension-v{{VERSION}}/extension/TESTING.md).
2. Download **pricewatch-extension-{{VERSION}}.zip** below and unzip it.
3. In Chrome, open `chrome://extensions`, turn on **Developer mode** (top right) and click **Load unpacked**.
   Choose the unzipped folder.
4. Pin PriceWatch from the puzzle-piece menu, open a product page in any shop and click the icon.

## Permissions

| Permission | Why |
| --- | --- |
| Read the current page (`activeTab`, `scripting`) | Only when you click the icon. The extension has no access to the sites you visit otherwise. |
| Storage | Remembers your guest id, so the popup and the web app show the same shelf. |
| `localhost` | Talks to your own PriceWatch. Nothing is sent anywhere else. |

## Feedback

Found a bug or a shop it cannot read?
[Report it here](https://github.com/{{REPO}}/issues/new?template=extension-feedback.yml) with the version
`{{VERSION}}`.

## Verify the download

`SHA256SUMS.txt` holds the zip's checksum (`shasum -a 256 -c SHA256SUMS.txt`). The zip was built by
GitHub Actions from this tag; check that with
`gh attestation verify pricewatch-extension-{{VERSION}}.zip --repo {{REPO}}`.
