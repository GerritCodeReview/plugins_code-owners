import 'chai/chai';
declare global {
  interface Window {
    assert: typeof chai.assert;
    expect: typeof chai.expect;
    sinon: typeof sinon;
  }
  let assert: typeof chai.assert;
  let expect: typeof chai.expect;
  let sinon: typeof sinon;
}
window.assert = chai.assert;
window.expect = chai.expect;
window.sinon = sinon;
