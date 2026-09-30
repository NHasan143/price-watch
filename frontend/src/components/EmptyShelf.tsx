import { Barcode } from './Barcode'
import { Icon } from './Icon'
import { LabelPrice } from './LabelPrice'

const STEPS = [
  { icon: 'link', text: 'Paste a product link from any shop' },
  { icon: 'tag', text: 'Set the price you’d be happy to pay' },
  { icon: 'check', text: 'The yellow sticker lands when it drops' },
] as const

/** First-use shelf: what a tracked product looks like, and one action to start. */
export function EmptyShelf({ onStart }: { onStart: () => void }) {
  return (
    <div className="empty-shelf">
      <div className="empty-copy">
        <h3>Your shelf is empty.</h3>
        <p>Add the first product you’re waiting on. PriceWatch keeps checking its price, so you don’t have to.</p>
        <ol className="empty-steps">
          {STEPS.map((step, index) => (
            <li key={step.icon}>
              <span className={`empty-step-icon${index === STEPS.length - 1 ? ' is-sticker' : ''}`}>
                <Icon name={step.icon} size={18} />
              </span>
              {step.text}
            </li>
          ))}
        </ol>
        <button type="button" className="btn btn-ink btn-large" onClick={onStart}>
          <Icon name="plus" size={18} />
          Add your first product
        </button>
      </div>

      <div className="empty-scene" aria-hidden="true">
        <div className="empty-scene-labels">
          <div className="empty-example">
            <span className="empty-example-tag">Example</span>
            <div className="label">
              <div className="label-top">
                <div className="label-name">
                  <h4 className="empty-example-name">Wireless Noise Cancelling Headphones</h4>
                  <span className="label-shop">shop.example</span>
                </div>
                <LabelPrice value={278} currency="USD" className="label-price-now" />
              </div>
              <p className="label-target">
                <span>
                  Your price <strong>$300.00</strong>
                </span>
                <span className="label-gap label-gap-under">$22.00 under</span>
              </p>
              <div className="label-foot">
                <Barcode seed="example" />
                <span className="label-code">#0000 · USD · checked just now</span>
              </div>
            </div>
            <p className="sticker">
              <span className="sticker-head">At your price</span>
              <span className="sticker-line">$22.00 under</span>
            </p>
          </div>
          <div className="empty-slot">
            <Icon name="plus" size={20} />
            <span>Your first label</span>
          </div>
        </div>
        <div className="empty-rail" />
      </div>
    </div>
  )
}
