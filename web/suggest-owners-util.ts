/**
 * @license
 * Copyright (C) 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import {BestSuggestionsLimit} from './code-owners-model.js';

/**
 * For each file calculates owners to display and group all files by those
 * owners. The function creates "fake" groups when one or more
 * reviewers are included in all owners of a file, but none of reviewers is
 * included in best reviewers for the file.
 *
 * Such situations are possible when user turns on "Show all owners", selects
 * one of newly displayed owners and then turns off "Show all owners". Without
 * "fake" groups a user can see inconsistent state in dialog.
 */

export function getDisplayOwnersGroups(files, allOwnersByPathMap,
    reviewersIdSet, allowAllOwnersSubstition) {
  const getDisplayOwnersFunc =
      !allowAllOwnersSubstition || allOwnersByPathMap.size === 0 ||
      reviewersIdSet.size === 0 ?
        file => file.info.owners :
        file => getDisplayOwners(file, allOwnersByPathMap, reviewersIdSet);
  return groupFilesByOwners(files, getDisplayOwnersFunc);
}

function getDisplayOwners(file, allOwnersByPathMap, reviewersIdSet) {
  const ownerSelected = owner => reviewersIdSet.has(owner.account._account_id);
  const defaultOwners = file.info.owners;
  if (!defaultOwners ||
      defaultOwners.owned_by_all_users ||
      defaultOwners.code_owners.some(ownerSelected)) {
    return defaultOwners;
  }
  const allOwners = allOwnersByPathMap.get(file.path);
  if (!allOwners) return defaultOwners;
  if (allOwners.owned_by_all_users) return allOwners;
  const selectedAllOwners = allOwners.code_owners.filter(ownerSelected);
  if (selectedAllOwners.length === 0) return defaultOwners;
  return {
    code_owners: selectedAllOwners.slice(0, BestSuggestionsLimit),
  };
}

function groupFilesByOwners(files, getDisplayOwnersFunc) {
  // Note: for renamed or moved files, they will have two entries in the map
  // we will treat them as two entries when group as well
  const ownersFilesMap = new Map();
  const failedToFetchFiles = new Set();
  for (const file of files) {
    // for files failed to fetch, add them to the special group
    if (file.info.error) {
      failedToFetchFiles.add(file);
      continue;
    }

    // do not include files still in fetching
    if (!file.info.owners) {
      continue;
    }
    const displayOwners = getDisplayOwnersFunc(file);

    const ownersKey = getOwnersGroupKey(displayOwners);
    ownersFilesMap.set(
        ownersKey,
        ownersFilesMap.get(ownersKey) || {files: [], owners: displayOwners}
    );
    ownersFilesMap.get(ownersKey).files.push(file);
  }
  const groupedItems = [];
  for (const ownersKey of ownersFilesMap.keys()) {
    const groupName = getGroupName(ownersFilesMap.get(ownersKey).files);
    groupedItems.push({
      groupName,
      files: ownersFilesMap.get(ownersKey).files,
      owners: ownersFilesMap.get(ownersKey).owners,
    });
  }

  if (failedToFetchFiles.size > 0) {
    const failedFiles = [...failedToFetchFiles];
    groupedItems.push({
      groupName: getGroupName(failedFiles),
      files: failedFiles,
      error: new Error(
          'Failed to fetch code owner info. Try to refresh the page.'),
    });
  }
  return groupedItems;
}

function getOwnersGroupKey(owners) {
  if (owners.owned_by_all_users) {
    return '__owned_by_all_users__';
  }
  const code_owners = owners.code_owners;
  return code_owners
      .map(owner => owner.account._account_id)
      .sort()
      .join(',');
}

function getGroupName(files) {
  const fileName = files[0].path.split('/').pop();
  return {
    name: fileName,
    prefix: files.length > 1 ? `+ ${files.length - 1} more` : '',
  };
}
