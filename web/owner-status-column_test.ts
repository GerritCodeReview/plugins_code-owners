import {assert} from '@open-wc/testing';

import {hasPath} from './owner-status-column';

suite('owner-status-column', () => {
  test('hasPath', () => {
    assert(hasPath(new Set(['/COMMIT']), '/COMMIT'));
    assert(hasPath(new Set(['/some/file']), 'some/file'));
  });
});
