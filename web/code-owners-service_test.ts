import {CodeOwnerService} from './code-owners-service.js';
import {
  RequestPayload,
  RestPluginApi,
} from '@gerritcodereview/typescript-api/rest.js';
import {
  ChangeInfo,
  HttpMethod,
} from '@gerritcodereview/typescript-api/rest-api.js';
import {SuggestionsType} from './code-owners-model.js';
import {assert} from '@open-wc/testing';
import sinon from 'sinon';

function flush() {
  return new Promise((resolve, _reject) => {
    setTimeout(resolve, 0);
  });
}

suite('code owners service tests', () => {
  let codeOwnersService: CodeOwnerService;
  const fakeRestApi = {
    send(
      _method: HttpMethod,
      _url: string,
      _payload?: RequestPayload,
      _errFn?: ErrorCallback,
      _contentType?: string
    ) {
      return Promise.resolve({});
    },
    getLoggedIn() {
      return Promise.resolve(true);
    },
    getAccount() {
      return Promise.resolve({email: 'someone@google.com'});
    },
  } as unknown as RestPluginApi;

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

  let getApiStub: sinon.SinonStub;

  setup(() => {
    getApiStub = sinon.stub(fakeRestApi, 'send');
    getApiStub.returns(Promise.resolve({}));
  });

  teardown(() => {
    getApiStub.reset();
    CodeOwnerService.reset();
    sinon.restore();
  });

  suite('basic api request tests', () => {
    setup(async () => {
      getApiStub
        .withArgs(
          sinon.match.any,
          `/changes/${fakeChange.project}~${fakeChange._number}/code_owners.status?limit=100000`,
          sinon.match.any,
          sinon.match.any
        )
        .returns(Promise.resolve(fakeStatus));
      codeOwnersService = CodeOwnerService.getOwnerService(fakeRestApi, {
        ...fakeChange,
      });
      await flush();
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
      assert.isTrue(getApiStub.called);
      assert.equal(
        getApiStub.lastCall.args[1],
        `/changes/${fakeChange.project}~${fakeChange._number}/code_owners.status?limit=100000`
      );
    });

    test('getSuggestion should kickoff the fetch', async () => {
      assert.equal(getApiStub.callCount, 2);
      getApiStub.resetHistory();
      await codeOwnersService.getSuggestedOwners(
        SuggestionsType.ALL_SUGGESTIONS
      );
      // 8 requests for getting file owners
      assert.equal(getApiStub.callCount, 8);
    });

    test('approved status calculation', async () => {
      const approved = await codeOwnersService.areAllFilesApproved();
      assert.equal(approved, false);
    });
  });

  suite('getOwnedPaths', () => {
    async function setupBranchConfig(disabled: boolean | undefined) {
      getApiStub
        .withArgs(
          sinon.match.any,
          `/projects/${fakeChange.project}/branches/` +
            `${fakeChange.branch}/code_owners.branch_config`,
          sinon.match.any,
          sinon.match.any
        )
        .returns(Promise.resolve({disabled}));
      codeOwnersService = CodeOwnerService.getOwnerService(fakeRestApi, {
        ...fakeChange,
      });
      await flush();
      getApiStub.resetHistory();
    }
    test('should not fetch if disabled', async () => {
      await setupBranchConfig(true);

      await codeOwnersService.getOwnedPaths();
      assert.equal(getApiStub.callCount, 0);
    });

    test('should fetch if enabled', async () => {
      await setupBranchConfig(false);

      await codeOwnersService.getOwnedPaths();
      assert.equal(getApiStub.callCount, 1);
    });

    test('should fetch if enabled by default', async () => {
      await setupBranchConfig(undefined);

      await codeOwnersService.getOwnedPaths();
      assert.equal(getApiStub.callCount, 1);
    });
  });

  suite('all approved case', () => {
    setup(async () => {
      getApiStub
        .withArgs(
          sinon.match.any,
          `/changes/${fakeChange.project}~${fakeChange._number}/code_owners.status?limit=100000`,
          sinon.match.any,
          sinon.match.any
        )
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
      await flush();
    });

    test('approved status calculation', async () => {
      const approved = await codeOwnersService.areAllFilesApproved();
      assert.equal(approved, true);
    });
  });
});
