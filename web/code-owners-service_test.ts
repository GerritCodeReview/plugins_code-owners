import './test-setup.js';
import {CodeOwnerService} from './code-owners-service.js';
import {RestPluginApi} from '@gerritcodereview/typescript-api/rest.js';
import {ChangeInfo} from '@gerritcodereview/typescript-api/rest-api.js';
import {SuggestionsType} from './code-owners-model.js';

function flush(cb: () => void) {
  setTimeout(cb, 0);
}

suite('code owners service tests', () => {
  let codeOwnersService: CodeOwnerService;
  const fakeRestApi = {
    get(_url: string) {
      return Promise.resolve({});
    },
    getLoggedIn() {
      return Promise.resolve(undefined);
    },
  } as unknown as RestPluginApi;
  const getApiStub = sinon.stub(fakeRestApi, 'get');
  getApiStub.returns(Promise.resolve({}));
  const fakeStatus = {
    // fake data with fake files
    patch_set_number: 1,
    file_code_owner_statuses: [
      {
        new_path_status: {
          path: 'a.js',
          status: 'INSUFFICIENT_REVIEWERS',
        },
      },
      {
        new_path_status: {
          path: 'b.js',
          status: 'INSUFFICIENT_REVIEWERS',
        },
      },
      {
        new_path_status: {
          path: 'c.ts',
          status: 'INSUFFICIENT_REVIEWERS',
        },
      },
      {
        change_type: 'RENAMED',
        old_path_status: {
          path: 'd.ts',
          status: 'INSUFFICIENT_REVIEWERS',
        },
        new_path_status: {
          path: 'd_edit.ts',
          status: 'INSUFFICIENT_REVIEWERS',
        },
      },
      {
        new_path_status: {
          path: 'e.ts',
          status: 'PENDING',
        },
      },
      {
        new_path_status: {
          path: 'f.ts',
          status: 'PENDING',
        },
      },
      {
        new_path_status: {
          path: 'g.ts',
          status: 'PENDING',
        },
      },
    ],
  };
  const fakeChange = {
    project: 'test',
    branch: 'main',
    _number: '123',
    revisions: {
      a: {
        _number: 1,
      },
    },
    current_revision: 'a',
  } as unknown as ChangeInfo;

  setup(() => {
    getApiStub.reset();
  });

  teardown(() => {
    sinon.restore();
  });

  suite('basic api request tests', () => {
    setup(async () => {
      getApiStub
        .withArgs(`/changes/${fakeChange._number}/code_owners.status`)
        .returns(Promise.resolve(fakeStatus));
      codeOwnersService = CodeOwnerService.getOwnerService(fakeRestApi, {
        ...fakeChange,
      });
    });

    test('getOwnerService - same change returns the same instance', () => {
      assert.equal(
        CodeOwnerService.getOwnerService(fakeRestApi, fakeChange),
        CodeOwnerService.getOwnerService(fakeRestApi, fakeChange)
      );
    });

    test('return a new instance when change changed', () => {
      assert.notEqual(
        CodeOwnerService.getOwnerService(fakeRestApi, {...fakeChange}),
        CodeOwnerService.getOwnerService(fakeRestApi, {...fakeChange})
      );
    });

    test('should fetch status after init', () => {
      assert.isTrue(getApiStub.calledOnce);
      assert.equal(
        getApiStub.lastCall.args[0],
        `/changes/${fakeChange._number}/code_owners.status`
      );
    });

    test('getSuggestion should kickoff the fetch', done => {
      codeOwnersService
        .getSuggestedOwners(SuggestionsType.ALL_SUGGESTIONS)
        .then(r => {
          // 6 requests for getting file owners + 1 status call
          assert.equal(getApiStub.callCount, 7);
          assert.equal(r.finished, false);
          setTimeout(() => {
            assert.equal(getApiStub.callCount, 9);
            done();
          }, 0);
        });
    });

    test('approved status calculation', done => {
      codeOwnersService.areAllFilesApproved().then(approved => {
        assert.equal(approved, false);
        done();
      });
    });
  });

  suite('all approved case', () => {
    setup(done => {
      getApiStub
        .withArgs(`/changes/${fakeChange._number}/code_owners.status`)
        .returns(
          Promise.resolve({
            // fake data with fake files
            patch_set_number: 1,
            file_code_owner_statuses: [
              {
                new_path_status: {
                  path: 'a.js',
                  status: 'APPROVED',
                },
              },
              {
                new_path_status: {
                  path: 'b.js',
                  status: 'APPROVED',
                },
              },
              {
                old_path_status: {
                  path: 'd.js',
                  status: 'APPROVED',
                },
                change_type: 'DELETED',
              },
            ],
          })
        );

      codeOwnersService = CodeOwnerService.getOwnerService(fakeRestApi, {
        ...fakeChange,
      });
      flush(done);
    });

    test('getSuggestion and finished in first batch', done => {
      codeOwnersService
        .getSuggestedOwners(SuggestionsType.ALL_SUGGESTIONS)
        .then(r => {
          // 1 status call
          assert.equal(getApiStub.callCount, 1);
          // initially false as getSuggestedOwners is synchrously
          assert.equal(r.finished, false);
          codeOwnersService
            .getSuggestedOwners(SuggestionsType.ALL_SUGGESTIONS)
            .then(nR => {
              assert.equal(nR.finished, true);
              done();
            });
        });
    });

    test('approved status calculation', done => {
      codeOwnersService.areAllFilesApproved().then(approved => {
        assert.equal(approved, true);
        done();
      });
    });
  });
});
