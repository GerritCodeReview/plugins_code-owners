!function(){"use strict";
/**
   * @license
   * Copyright (C) 2020 The Android Open Source Project
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
   */const e="NotLoaded",t="Loaded",s="Loading",o="Enabled",n="ServerConfigurationError",r="Failed";function i(e){return e===n||e===r}const a={BEST_SUGGESTIONS:"BEST_SUGGESTIONS",ALL_SUGGESTIONS:"ALL_SUGGESTIONS"};class l extends EventTarget{constructor(t){super(),this.change=t,this.branchConfig=void 0,this.status=void 0,this.userRole=void 0,this.isCodeOwnerEnabled=void 0,this.areAllFilesApproved=void 0,this.suggestionsByTypes={};for(const t of Object.values(a))this.suggestionsByTypes[t]={files:void 0,state:e,loadProgress:void 0};this.selectedSuggestionsType=a.BEST_SUGGESTIONS,this.showSuggestions=!1,this.pluginStatus=void 0}get selectedSuggestions(){return this.suggestionsByTypes[this.selectedSuggestionsType]}setBranchConfig(e){this.branchConfig!==e&&(this.branchConfig=e,this._firePropertyChanged("branchConfig"))}setStatus(e){this.status!==e&&(this.status=e,this._firePropertyChanged("status"))}setUserRole(e){this.userRole!==e&&(this.userRole=e,this._firePropertyChanged("userRole"))}setIsCodeOwnerEnabled(e){this.isCodeOwnerEnabled!==e&&(this.isCodeOwnerEnabled=e,this._firePropertyChanged("isCodeOwnerEnabled"))}setAreAllFilesApproved(e){this.areAllFilesApproved!==e&&(this.areAllFilesApproved=e,this._firePropertyChanged("areAllFilesApproved"))}setSuggestionsFiles(e,t){const s=this.suggestionsByTypes[e];s.files!==t&&(s.files=t,this._fireSuggestionsChanged(e,"files"))}setSuggestionsState(e,t){const s=this.suggestionsByTypes[e];s.state!==t&&(s.state=t,this._fireSuggestionsChanged(e,"state"))}setSuggestionsLoadProgress(e,t){const s=this.suggestionsByTypes[e];s.loadProgress!==t&&(s.loadProgress=t,this._fireSuggestionsChanged(e,"loadProgress"))}setSelectedSuggestionType(e){this.selectedSuggestionsType!==e&&(this.selectedSuggestionsType=e,this._firePropertyChanged("selectedSuggestionsType"),this._firePropertyChanged("selectedSuggestions"))}setShowSuggestions(e){this.showSuggestions!==e&&(this.showSuggestions=e,this._firePropertyChanged("showSuggestions"))}setPluginEnabled(e){this._setPluginStatus({state:e?o:"Disabled"})}setServerConfigurationError(e){this._setPluginStatus({state:n,failedMessage:e})}setPluginFailed(e){this._setPluginStatus({state:r,failedMessage:e})}_setPluginStatus(e){this._arePluginStatusesEqual(this.pluginStatus,e)||(this.pluginStatus=e,this._firePropertyChanged("pluginStatus"))}_arePluginStatusesEqual(e,t){return void 0===e||void 0===t?e===t:e.state===t.state&&(!i(e.state)||e.failedMessage===t.failedMessage)}_firePropertyChanged(e){this.dispatchEvent(new CustomEvent("model-property-changed",{detail:{propertyName:e}}))}_fireSuggestionsChanged(e,t){this._firePropertyChanged(`suggestionsByTypes.${e}.${t}`),e===this.selectedSuggestionsType&&this._firePropertyChanged(`selectedSuggestions.${t}`)}static getModel(e){return this.model&&this.model.change===e||(this.model=new l(e)),this.model}}
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
   */class c extends Error{constructor(e){super(),this.response=e}}class d extends Error{constructor(e){super(e)}}async function g(e){const t=await e.text();return t?`${e.status}: ${t}`:`${e.status}`}class u{constructor(e){this.restApi=e}async _get(e){const t=(e,t)=>{if(t)throw t;if(e)throw new c(e);throw new Error("Generic REST API error")};try{return await this.restApi.send("GET",e,void 0,t)}catch(e){if(e instanceof c&&409===e.response.status)return g(e.response).then((e=>{throw new d(e)}));throw e}}listOwnerStatus(e){return this._get(`/changes/${e}/code_owners.status`)}listOwnersForPath(e,t,s){return this._get(`/changes/${e}/revisions/current/code_owners/${encodeURIComponent(t)}?limit=${s}&o=DETAILS`)}getConfigForPath(e,t,s){return this._get(`/projects/${encodeURIComponent(e)}/branches/${encodeURIComponent(t)}/code_owners.config/${encodeURIComponent(s)}`)}async getBranchConfig(e,t){try{const s=await this._get(`/projects/${encodeURIComponent(e)}/branches/${encodeURIComponent(t)}/code_owners.branch_config`);return!s.override_approval||s.override_approval instanceof Array?s:{...s,override_approval:[s.override_approval]}}catch(e){if(e instanceof c)return 404===e.response.status?{disabled:!0}:g(e.response).then((e=>{throw new Error(e)}));throw e}}}class h{constructor(e,t){this.codeOwnerApi=e,this.change=t,this.promises={}}_fetchOnce(e,t){return this.promises[e]||(this.promises[e]=t()),this.promises[e]}getAccount(){return this._fetchOnce("getAccount",(()=>this._getAccount()))}async _getAccount(){if(await this.codeOwnerApi.restApi.getLoggedIn())return await this.codeOwnerApi.restApi.getAccount()}listOwnerStatus(){return this._fetchOnce("listOwnerStatus",(()=>this.codeOwnerApi.listOwnerStatus(this.change._number)))}getBranchConfig(){return this._fetchOnce("getBranchConfig",(()=>this.codeOwnerApi.getBranchConfig(this.change.project,this.change.branch)))}}
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
   */const p="INSUFFICIENT_REVIEWERS",w="PENDING",m="APPROVED";class _{constructor(e,t,s,o,n){this._fetchedOwners=new Map,this._ownersLimit=o,this._paused=!0,this._pausedFilesFetcher=[],this._filesToFetch=s,this._fetchFilesPromises=[],this._codeOwnerApi=e,this._changeId=t;for(let e=0;e<n;e++)this._fetchFilesPromises.push(this._fetchFiles())}async _fetchFiles(){for(;;){const e=await this._getNextFilePath();if(!e)return;try{this._fetchedOwners.set(e,{owners:await this._codeOwnerApi.listOwnersForPath(this._changeId,e,this._ownersLimit)})}catch(t){this._fetchedOwners.set(e,{error:t})}}}async _getNextFilePath(){return this._paused&&await new Promise((e=>this._pausedFilesFetcher.push(e))),0===this._filesToFetch.length?null:this._filesToFetch.splice(0,1)[0]}async waitFetchComplete(){await Promise.allSettled(this._fetchFilesPromises)}resume(){if(this._paused){this._paused=!1;for(const e of this._pausedFilesFetcher.splice(0,this._pausedFilesFetcher.length))e()}}pause(){this._paused=!0}getFetchedOwners(){return this._fetchedOwners}getFiles(){const e=[];for(const[t,s]of this._fetchedOwners.entries())e.push({path:t,info:s});return e}}class f{constructor(e,t,s){this.change=t,this.options=s,this._totalFetchCount=0,this._status=0,this._codeOwnerApi=new u(e)}getStatus(){return this._status}getProgressString(){return this._ownersFetcher&&0!==this._totalFetchCount?`${this._ownersFetcher.getFetchedOwners().size} out of ${this._totalFetchCount} files have returned suggested owners.`:"Loading suggested owners ..."}getFiles(){return this._ownersFetcher?this._ownersFetcher.getFiles():[]}async fetchSuggestedOwners(e){if(0!==this._status)return void await this._ownersFetcher.waitFetchComplete();const t=this._getFilesToFetch(e);this._totalFetchCount=t.length,this._ownersFetcher=new _(this._codeOwnerApi,this.change.id,t,this.options.ownersLimit,this.options.maxConcurrentRequests),this._status=1,this._ownersFetcher.resume(),await this._ownersFetcher.waitFetchComplete(t),this._status=2}_getFilesToFetch(e){const t=[...e.entries()].reduce(((e,[t,s])=>(e[s.status]&&e[s.status].push(t),e)),{[w]:[],[p]:[],[m]:[]});return t.INSUFFICIENT_REVIEWERS.concat(t.PENDING).concat(t.APPROVED)}pause(){this._ownersFetcher&&this._ownersFetcher.pause()}resume(){this._ownersFetcher&&this._ownersFetcher.resume()}reset(){this._totalFetchCount=0,this.ownersFetcher=null,this._status=0}}
/**
   * @license
   * Copyright (C) 2020 The Android Open Source Project
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
   */class v{constructor(e,t,s={}){this.restApi=e,this.change=t;const o=new u(e);this.codeOwnersCacheApi=new h(o,t);const n={maxConcurrentRequests:s.maxConcurrentRequests||10};this.ownersProviders={[a.BEST_SUGGESTIONS]:new f(e,t,{...n,ownersLimit:5}),[a.ALL_SUGGESTIONS]:new f(e,t,{...n,ownersLimit:1e3})}}async prefetch(){try{await Promise.all([this.codeOwnersCacheApi.getAccount(),this.getStatus()])}catch{}}async getLoggedInUserInitialRole(){const e=await this.codeOwnersCacheApi.getAccount();if(!e)return"ANONYMOUS";const t=this.change;if(t.revisions&&t.current_revision&&t.revisions[t.current_revision]){const s=t.revisions[t.current_revision].commit;if(s&&s.author&&e.email&&s.author.email===e.email)return"AUTHOR"}if(t.owner._account_id===e._account_id)return"CHANGE_OWNER";if(t.reviewers){if(this._accountInReviewers(t.reviewers.REVIEWER,e))return"REVIEWER";if(this._accountInReviewers(t.reviewers.CC,e))return"CC";if(this._accountInReviewers(t.reviewers.REMOVED,e))return"REMOVED_REVIEWER"}return"OTHER"}_accountInReviewers(e,t){return!!e&&e.some((e=>e._account_id===t._account_id))}async getStatus(){const e=await this._getStatus();return e.enabled&&this._isOnOlderPatchset(e.patchsetNumber)?(this.reset(),this.prefetch(),await this.getStatus()):e}async _getStatus(){if(!await this.isCodeOwnerEnabled())return{patchsetNumber:0,enabled:!1,codeOwnerStatusMap:new Map,rawStatuses:[],newerPatchsetUploaded:!1};const e=await this.codeOwnersCacheApi.listOwnerStatus();return{enabled:!0,patchsetNumber:e.patch_set_number,codeOwnerStatusMap:this._formatStatuses(e.file_code_owner_statuses),rawStatuses:e.file_code_owner_statuses,newerPatchsetUploaded:this._isOnNewerPatchset(e.patch_set_number)}}async areAllFilesApproved(){const{rawStatuses:e}=await this.getStatus();return!e.some((e=>{const t=e.old_path_status,s=e.new_path_status;return s&&s.status!==m||t&&t.status!==m}))}async getSuggestedOwners(e){const{codeOwnerStatusMap:t}=await this.getStatus(),s=this.ownersProviders[e];return await s.fetchSuggestedOwners(t),{finished:2===s.getStatus(),status:s.getStatus(),progress:s.getProgressString(),files:this._getFilesWithStatuses(t,s.getFiles())}}async getSuggestedOwnersProgress(e){const{codeOwnerStatusMap:t}=await this.getStatus(),s=this.ownersProviders[e];return{finished:2===s.getStatus(),status:s.getStatus(),progress:s.getProgressString(),files:this._getFilesWithStatuses(t,s.getFiles())}}pauseSuggestedOwnersLoading(e){this.ownersProviders[e].pause()}resumeSuggestedOwnersLoading(e){this.ownersProviders[e].resume()}_formatStatuses(e){return e.reduce(((e,t)=>{const s=t.new_path_status,o=t.old_path_status;return o&&e.set(o.path,{changeType:t.change_type,status:o.status,newPath:s?s.path:null}),s&&e.set(s.path,{changeType:t.change_type,status:s.status,oldPath:o?o.path:null}),e}),new Map)}_computeFileStatus(e,t){if(e.get(t).oldPath)return"Renamed"}_getFilesWithStatuses(e,t){return t.map((t=>({path:t.path,info:t.info,status:this._computeFileStatus(e,t.path)})))}_isOnNewerPatchset(e){return e>this.change.revisions[this.change.current_revision]._number}_isOnOlderPatchset(e){return e<this.change.revisions[this.change.current_revision]._number}reset(){for(const e of Object.values(this.ownersProviders))e.reset();const e=new u(this.restApi);this.codeOwnersCacheApi=new h(e,this.change)}async getBranchConfig(){return this.codeOwnersCacheApi.getBranchConfig()}async isCodeOwnerEnabled(){if("ABANDONED"===this.change.status||"MERGED"===this.change.status)return!1;const e=await this.codeOwnersCacheApi.getBranchConfig();return e&&!e.disabled}static getOwnerService(e,t){return this.ownerService&&this.ownerService.change===t||(this.ownerService=new v(e,t,{maxConcurrentRequests:6}),this.ownerService.prefetch()),this.ownerService}}
/**
   * @license
   * Copyright (C) 2020 The Android Open Source Project
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
   */class S{constructor(e,t){this.ownersService=e,this.ownersModel=t}async _loadProperty(e,t,s){if(void 0!==this.ownersModel[e])return;let o;try{o=await t()}catch(e){return e instanceof d?void this.ownersModel.setServerConfigurationError(e.message):(console.error(e),void this.ownersModel.setPluginFailed(e.message))}void 0===this.ownersModel[e]&&s(o)}async loadBranchConfig(){await this._loadProperty("branchConfig",(()=>this.ownersService.getBranchConfig()),(e=>this.ownersModel.setBranchConfig(e)))}async loadStatus(){await this._loadProperty("status",(()=>this.ownersService.getStatus()),(e=>this.ownersModel.setStatus(e)))}async loadUserRole(){await this._loadProperty("userRole",(()=>this.ownersService.getLoggedInUserInitialRole()),(e=>this.ownersModel.setUserRole(e)))}async loadPluginStatus(){await this._loadProperty("pluginStatus",(()=>this.ownersService.isCodeOwnerEnabled()),(e=>this.ownersModel.setPluginEnabled(e)))}async loadAreAllFilesApproved(){await this._loadProperty("areAllFilesApproved",(()=>this.ownersService.areAllFilesApproved()),(e=>this.ownersModel.setAreAllFilesApproved(e)))}async loadSuggestions(o){if(this.pauseActiveSuggestedOwnersLoading(),this.activeLoadSuggestionType=o,this.ownersModel.suggestionsByTypes[o].state===s)return void this.ownersService.resumeSuggestedOwnersLoading(o);if(this.ownersModel.suggestionsByTypes[o].state!==e)return;let n;this.ownersModel.setSuggestionsState(o,s);try{n=await this.ownersService.getSuggestedOwners(o)}catch(e){return console.error(e),this.ownersModel.setSuggestionsState(o,"LoadFailed"),void(this.ownersModel.selectedSuggestionsType===o&&this.ownersModel.setPluginFailed(e.message))}this.ownersModel.setSuggestionsFiles(o,n.files),this.ownersModel.setSuggestionsState(o,t)}pauseActiveSuggestedOwnersLoading(){this.activeLoadSuggestionType&&this.ownersService.pauseSuggestedOwnersLoading(this.activeLoadSuggestionType)}async updateLoadSelectedSuggestionsProgress(){const e=this.ownersModel.selectedSuggestionsType;let t;try{t=await this.ownersService.getSuggestedOwnersProgress(e)}catch{return}this.ownersModel.setSuggestionsLoadProgress(e,t.progress),this.ownersModel.setSuggestionsFiles(e,t.files)}}
/**
   * @license
   * Copyright (C) 2020 The Android Open Source Project
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
   */const y=Polymer.dedupingMixin((e=>class extends e{constructor(...e){super(...e),this.modelLoader=void 0}static get properties(){return{change:Object,reporting:Object,restApi:Object,model:{type:Object,observer:"_modelChanged"}}}static get observers(){return["onInputChanged(restApi, change, reporting)"]}onInputChanged(e,t,s){if([e,t,s].includes(void 0))return this.model=void 0,void(this.modelLoader=void 0);const o=v.getOwnerService(this.restApi,this.change),n=l.getModel(t);this.modelLoader=new S(o,n),this.model=n}_modelChanged(e){if(this.modelPropertyChangedUnsubscriber&&(this.modelPropertyChangedUnsubscriber(),this.modelPropertyChangedUnsubscriber=void 0),!e)return;const t=e=>{this.notifyPath(`model.${e.detail.propertyName}`)};e.addEventListener("model-property-changed",t),this.modelPropertyChangedUnsubscriber=()=>{e.removeEventListener("model-property-changed",t)},this.loadPropertiesAfterModelChanged()}loadPropertiesAfterModelChanged(){}}));
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
   */function b(e){return{name:e[0].path.split("/").pop(),prefix:e.length>1?`+ ${e.length-1} more`:""}}
/**
   * @license
   * Copyright (C) 2020 The Android Open Source Project
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
   */class C extends Polymer.Element{static get is(){return"owner-group-file-list"}static get properties(){return{files:Array}}static get template(){return Polymer.html`
      <style include="shared-styles">
        :host {
          display: block;
          max-height: 500px;
          overflow: auto;
        }
        span {
          display: inline-block;
          border-radius: var(--border-radius);
          margin-left: var(--spacing-s);
          padding: 0 var(--spacing-m);
          color: var(--primary-text-color);
          font-size: var(--font-size-small);
        }
        .Renamed {
          background-color: var(--dark-remove-highlight-color);
          margin: var(--spacing-s) 0;
        }
      </style>
      <ul>
        <template
          is="dom-repeat"
          items="[[files]]"
          as="file"
        >
          <li>
            [[computeFilePath(file)]]<!--
            --><strong>[[computeFileName(file)]]</strong>
            <template is="dom-if" if="[[file.status]]">
              <span class$="[[file.status]]">
                [[file.status]]
              </span>
            </template>
          </li>
        </template>
      </ul>
    `}computeFilePath(e){const t=e.path.split("/");return t.slice(0,t.length-1).join("/")+"/"}computeFileName(e){return e.path.split("/").pop()}}customElements.define(C.is,C);class O extends(y(Polymer.Element)){static get is(){return"suggest-owners"}static get template(){return Polymer.html`
      <style include="shared-styles">
        :host {
          display: block;
          background-color: var(--view-background-color);
          border: 1px solid var(--view-background-color);
          border-radius: var(--border-radius);
          box-shadow: var(--elevation-level-1);
          padding: 0 var(--spacing-m);
          margin: var(--spacing-m) 0;
        }
        .loadingSpin {
          display: inline-block;
        }
        li {
          list-style: none;
        }
        .suggestion-container {
          /* TODO: TBD */
          max-height: 300px;
          overflow-y: auto;
        }
        .flex-break {
          height: 0;
          flex-basis: 100%;
        }
        .suggestion-row, .show-all-owners-row {
          display: flex;
          flex-direction: row;
          align-items: flex-start;
        }
        .suggestion-row {
          flex-wrap: wrap;
          border-top: 1px solid var(--border-color);
          padding: var(--spacing-s) 0;
        }
        .show-all-owners-row {
          padding: var(--spacing-m) var(--spacing-xl) var(--spacing-s) 0;
        }
        .show-all-owners-row .loading {
          padding: 0;
        }
        .show-all-owners-row .show-all-label {
          margin-left: auto; /* align label to the right */
        }
        .suggestion-row-indicator {
          margin-right: var(--spacing-s);
          visibility: hidden;
          line-height: 26px;
        }
        .suggestion-row-indicator[visible] {
          visibility: visible;
        }
        .suggestion-row-indicator[visible] iron-icon {
          color: var(--link-color);
          vertical-align: top;
          position: relative;
          --iron-icon-height: 18px;
          --iron-icon-width: 18px;
          top: 4px; /* (26-18)/2 - 26px line-height and 18px icon */
        }
        .suggestion-group-name {
          width: 260px;
          line-height: 26px;
          text-overflow: ellipsis;
          overflow: hidden;
          padding-right: var(--spacing-s);
          white-space: nowrap;
        }
        .group-name-content {
          display: flex;
          align-items: center;
        }
        .group-name-content .group-name {
          white-space: nowrap;
          overflow: hidden;
          text-overflow: ellipsis;
        }
        .group-name-prefix {
          padding-left: var(--spacing-s);
          white-space: nowrap;
          color: var(--deemphasized-text-color);
        }
        .suggested-owners {
          --account-gap: var(--spacing-xs);
          --negative-account-gap: calc(-1*var(--account-gap));
          margin: var(--negative-account-gap) 0 0 var(--negative-account-gap);
          flex: 1;
        }
        .fetch-error-content,
        .owned-by-all-users-content,
        .no-owners-content {
          line-height: 26px;
          flex: 1;
          padding-left: var(--spacing-m);
        }

        .owned-by-all-users-content iron-icon {
          width: 16px;
          height: 16px;
          padding-top: 5px;
        }
        
        .fetch-error-content {
          color: var(--error-text-color);
        }
        .no-owners-content a {
          padding-left: var(--spacing-s);
        }
        .no-owners-content a iron-icon {
          width: 16px;
          height: 16px;
          padding-top: 5px;
        }
        gr-account-label {
          display: inline-block;
          padding: var(--spacing-xs) var(--spacing-m);
          user-select: none;
          border: 1px solid transparent;
          --label-border-radius: 8px;
          /* account-max-length defines the max text width inside account-label.
           With 60px the gr-account-label always has width <= 100px and 5 labels
           are always fit in a single row */
          --account-max-length: 60px;
          border: 1px solid var(--border-color);
          margin: var(--account-gap) 0 0 var(--account-gap)
        }
        gr-account-label:focus {
          outline: none;
        }
        gr-account-label:hover {
          box-shadow: var(--elevation-level-1);
          cursor: pointer;
        }
        gr-account-label[selected] {
          background-color: var(--chip-selected-background-color);
          border: 1px solid var(--chip-selected-background-color);
          color: var(--chip-selected-text-color);
        }
        gr-hovercard {
          max-width: 800px;
        }
        .loading {
          display: flex;
          align-content: center;
          align-items: center;
          justify-content: center;
          padding: var(--spacing-m);
        }
        .loadingSpin {
          width: 18px;
          height: 18px;
          margin-right: var(--spacing-m);
        }
      </style>
      <ul class="suggestion-container">
        <li class="show-all-owners-row">
          <p class="loading" hidden="[[!isLoading]]">
            <span class="loadingSpin"></span>
            [[progressText]]
          </p>
          <label class="show-all-label">
            <input
              id="showAllOwnersCheckbox"
              type="checkbox"
              checked="{{_showAllOwners::change}}"
            />
            Show all owners
          </label>
        </li>
      </ul>
      <ul class="suggestion-container">
        <template
          is="dom-repeat"
          items="[[suggestedOwners]]"
          as="suggestion"
          index-as="suggestionIndex"
        >
          <li class="suggestion-row">
            <div
              class="suggestion-row-indicator"
              visible$="[[suggestion.hasSelected]]"
            >
              <iron-icon icon="gr-icons:check-circle"></iron-icon>
            </div>
            <div class="suggestion-group-name">
              <div class="group-name-content">
                <span class="group-name">
                  [[suggestion.groupName.name]]
                </span>
                <template is="dom-if" if="[[suggestion.groupName.prefix]]">
                  <span class="group-name-prefix">
                    ([[suggestion.groupName.prefix]])
                  </span>
                </template>
                <gr-hovercard hidden="[[suggestion.expanded]]">
                  <owner-group-file-list
                    files="[[suggestion.files]]"
                  >
                  </owner-group-file-list>
                </gr-hovercard>
              </div>
              <owner-group-file-list
                hidden="[[!suggestion.expanded]]"
                files="[[suggestion.files]]"
              ></owner-group-file-list>
            </div>
            <template is="dom-if" if="[[suggestion.error]]">
              <div class="fetch-error-content">
                [[suggestion.error]]
                <a on-click="_showErrorDetails"
              </div>
            </template>
            <template is="dom-if" if="[[!suggestion.error]]">
              <template is="dom-if" if="[[!_areOwnersFound(suggestion.owners)]]">
                <div class="no-owners-content">
                  <span>Not found</span>
                  <a on-click="_reportDocClick" href="https://gerrit.googlesource.com/plugins/code-owners/+/HEAD/resources/Documentation/how-to-use.md#no-code-owners-found" target="_blank">
                    <iron-icon icon="gr-icons:help-outline" title="read documentation"></iron-icon>
                  </a>
                </div>
              </template>
              <template is="dom-if" if="[[suggestion.owners.owned_by_all_users]]">
                <div class="owned-by-all-users-content">
                  <iron-icon icon="gr-icons:info" ></iron-icon>
                  <span>[[_getOwnedByAllUsersContent(isLoading, suggestedOwners)]]</span>
                </div>
              </template>
              <template is="dom-if" if="[[!suggestion.owners.owned_by_all_users]]">
                <template is="dom-if" if="[[_showAllOwners]]">
                  <div class="flex-break"></div>
                </template>
                <ul class="suggested-owners">
                  <template
                    is="dom-repeat"
                    items="[[suggestion.owners.code_owners]]"
                    as="owner"
                    index-as="ownerIndex"
                  ><!--
                    --><gr-account-label
                      data-suggestion-index$="[[suggestionIndex]]"
                      data-owner-index$="[[ownerIndex]]"
                      account="[[owner.account]]"
                      selected$="[[isSelected(owner)]]"
                      on-click="toggleAccount">
                    </gr-account-label><!--
                --></template>
                </ul>
              </template>
            </template>
          </li>
        </template>
      </ul>
    `}static get properties(){return{hidden:{type:Boolean,value:!0,reflectToAttribute:!0,computed:"_isHidden(model.showSuggestions)"},suggestedOwners:Array,isLoading:{type:Boolean,value:!0},reviewers:{type:Array},_reviewersIdSet:{type:Object,computed:"_getReviewersIdSet(reviewers)"},pendingReviewers:Array,_showAllOwners:{type:Boolean,value:!1,observer:"_showAllOwnersChanged"},_allOwnersByPathMap:{type:Object,computed:`_getOwnersByPathMap(model.suggestionsByTypes.${a.ALL_SUGGESTIONS}.files)`}}}static get observers(){return["_onReviewerChanged(reviewers)","_onShowSuggestionsChanged(model.showSuggestions)","_onShowSuggestionsTypeChanged(model.showSuggestions,model.selectedSuggestionsType)","_onSuggestionsStateChanged(model.selectedSuggestions.state)","_onSuggestionsFilesChanged(model.selectedSuggestions.files, _allOwnersByPathMap, _reviewersIdSet, model.selectedSuggestionsType,model.selectedSuggestions.state)","_onSuggestionsLoadProgressChanged(model.selectedSuggestions.loadProgress)"]}constructor(){super(),this.reportedEvents={};for(const e of Object.values(a))this.reportedEvents[e]={fetchingStart:!1,fetchingFinished:!1}}disconnectedCallback(){super.disconnectedCallback(),this._stopUpdateProgressTimer(),this.modelLoader&&this.modelLoader.pauseActiveSuggestedOwnersLoading()}_onShowSuggestionsChanged(e){e&&Polymer.Async.timeOut.run((()=>this.click()),100)}_onShowSuggestionsTypeChanged(e,t){e?(this.modelLoader.loadSuggestions(t),this.modelLoader.updateLoadSelectedSuggestionsProgress(),this.reportedEvents[t].fetchingStart||(this.reportedEvents[t].fetchingStart=!0,this.reporting.reportLifeCycle("owners-suggestions-fetching-start",{type:t}))):this.modelLoader.pauseActiveSuggestedOwnersLoading()}_startUpdateProgressTimer(){this._progressUpdateTimer||(this._progressUpdateTimer=setInterval((()=>{this.modelLoader.updateLoadSelectedSuggestionsProgress()}),1e3))}_stopUpdateProgressTimer(){this._progressUpdateTimer&&(clearInterval(this._progressUpdateTimer),this._progressUpdateTimer=void 0)}_onSuggestionsStateChanged(e){this._stopUpdateProgressTimer(),e===s&&this._startUpdateProgressTimer(),this.isLoading=e===s}_isHidden(e){return!e}loadPropertiesAfterModelChanged(){super.loadPropertiesAfterModelChanged(),this._stopUpdateProgressTimer(),this.modelLoader.loadAreAllFilesApproved()}_getReviewersIdSet(e){return new Set((e||[]).map((e=>e._account_id)))}_onSuggestionsFilesChanged(e,s,o,n,r){if(void 0===e||void 0===s||void 0===o||void 0===n||void 0===r)return;const i=function l(e,t,s,o){return function n(e,t){const s=new Map,o=new Set;for(const r of e){if(r.info.error){o.add(r);continue}if(!r.info.owners)continue;const e=t(r),i=(n=e).owned_by_all_users?"__owned_by_all_users__":n.code_owners.map((e=>e.account._account_id)).sort().join(",");s.set(i,s.get(i)||{files:[],owners:e}),s.get(i).files.push(r)}var n;const r=[];for(const e of s.keys()){const t=b(s.get(e).files);r.push({groupName:t,files:s.get(e).files,owners:s.get(e).owners})}if(o.size>0){const e=[...o];r.push({groupName:b(e),files:e,error:new Error("Failed to fetch code owner info. Try to refresh the page.")})}return r}(e,o&&0!==t.size&&0!==s.size?e=>function o(e,t,s){const o=e=>s.has(e.account._account_id),n=e.info.owners;if(!n||n.owned_by_all_users||n.code_owners.some(o))return n;const r=t.get(e.path);if(!r)return n;if(r.owned_by_all_users)return r;const i=r.code_owners.filter(o);return 0===i.length?n:{code_owners:i.slice(0,5)}}(e,t,s):e=>e.info.owners)}(e,s,o,n!==a.ALL_SUGGESTIONS);if(this._updateSuggestions(i),this._updateAllChips(this._currentReviewers),r===t&&!this.reportedEvents[n].fetchingFinished){this.reportedEvents[n].fetchingFinished=!0;const e=i.reduce(((e,t)=>(e.totalGroups++,e.stats.push([t.files.length,t.owners&&t.owners.code_owners?t.owners.code_owners.length:0]),e)),{totalGroups:0,stats:[],type:n});this.reporting.reportLifeCycle("owners-suggestions-fetching-finished",e)}}_getOwnersByPathMap(e){return new Map((e||[]).filter((e=>!e.info.error&&e.info.owners)).map((e=>[e.path,e.info.owners])))}_onSuggestionsLoadProgressChanged(e){this.progressText=e}_updateSuggestions(e){const t=e.map((e=>this.formatSuggestionInfo(e))),s=t.findIndex((e=>e.owners.owned_by_all_users));s>=0&&t.push(t.splice(s,1)[0]),this.suggestedOwners=t}_onReviewerChanged(e){this._currentReviewers=e,this._updateAllChips(e)}formatSuggestionInfo(e){const t={};if(t.groupName=e.groupName,t.files=e.files.slice(),e.owners){const s=(e.owners.code_owners||[]).map((e=>{const t={...e},s=this.change.reviewers.REVIEWER;return s&&s.find((t=>t._account_id===e._account_id))&&(t.selected=!0),t}));t.owners={owned_by_all_users:!!e.owners.owned_by_all_users,code_owners:s}}else t.owners={owned_by_all_users:!1,code_owners:[]};return t.error=e.error,t}addAccount(e){e.selected=!0,this.dispatchEvent(new CustomEvent("add-reviewer",{detail:{reviewer:{...e.account,_pendingAdd:!0}},composed:!0,bubbles:!0})),this.reporting.reportInteraction("add-reviewer")}removeAccount(e){e.selected=!1,this.dispatchEvent(new CustomEvent("remove-reviewer",{detail:{reviewer:{...e.account,_pendingAdd:!0}},composed:!0,bubbles:!0})),this.reporting.reportInteraction("remove-reviewer")}toggleAccount(e){const t=e.currentTarget,s=this.suggestedOwners[t.dataset.suggestionIndex].owners.code_owners[t.dataset.ownerIndex];this.isSelected(s)?this.removeAccount(s):this.addAccount(s)}_updateAllChips(e){this.suggestedOwners&&e&&this.suggestedOwners.forEach(((t,s)=>{let o=!1;t.owners.code_owners.forEach(((t,n)=>{e.some((e=>e._account_id===t.account._account_id))?(this.set(["suggestedOwners",s,"owners","code_owners",n],{...t,selected:!0}),o=!0):this.set(["suggestedOwners",s,"owners","code_owners",n],{...t,selected:!1})})),t.owners.owned_by_all_users&&e.some((e=>!e.tags||e.tags.indexOf("SERVICE_USER")<0))&&(o=!0),this.set(["suggestedOwners",s,"hasSelected"],o)}))}isSelected(e){return e.selected}_reportDocClick(){this.reporting.reportInteraction("code-owners-doc-click",{section:"no owners found"})}_areOwnersFound(e){return e.code_owners.length>0||!!e.owned_by_all_users}_getOwnedByAllUsersContent(e,t){return e?"Any user can approve":t&&1===t.length?"Any user can approve. Please select a user manually":"Any user from the other files can approve"}_showAllOwnersChanged(e){this.model&&this.model.setSelectedSuggestionType(e?a.ALL_SUGGESTIONS:a.BEST_SUGGESTIONS)}}customElements.define(O.is,O);
/**
   * @license
   * Copyright (C) 2020 The Android Open Source Project
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
const A=["/COMMIT_MSG","/MERGE_LIST","/PATCHSET_LEVEL"],P="pending",E="pending-old-path",x="missing",F="missing-old-path",T="approved",I="error",L="error-old-path",M=[I,L,x,P,F,E,T],R={[P]:"gr-icons:schedule",[x]:"gr-icons:close",[E]:"gr-icons:schedule",[F]:"gr-icons:close",[T]:"gr-icons:check",[I]:"gr-icons:info-outline",[I]:"gr-icons:info-outline"},k={[P]:"Pending code owner approval",[x]:"Missing code owner approval",[E]:"Pending code owner approval on pre-renamed file",[F]:"Missing code owner approval on pre-renamed file",[T]:"Approved by code owner",[I]:"Failed to fetch code owner status",[L]:"Failed to fetch code owner status"};class U extends(y(Polymer.Element)){static get properties(){return{patchRange:Object,hidden:{type:Boolean,reflectToAttribute:!0,computed:"computeHidden(change, patchRange, model.status.newerPatchsetUploaded)"}}}computeHidden(e,t,s){return!![e,t,s].includes(void 0)||!(!e.requirements||e.requirements.find((e=>"code-owners"===e.type)))||!!s||void 0!==t.patchNum&&`${t.patchNum}`!=`${e.revisions[e.current_revision]._number}`}}class N extends U{static get is(){return"owner-status-column-header"}static get template(){return Polymer.html`
         <style include="shared-styles">
         :host(:not([hidden])) {
           display: block;
           padding-right: var(--spacing-m);
           width: 3em;
         }
         </style>
         <div></div>
       `}}customElements.define(N.is,N);class B extends U{static get is(){return"owner-status-column-content"}static get properties(){return{path:String,oldPath:String,cleanlyMergedPaths:Array,cleanlyMergedOldPaths:Array,ownerService:Object,statusIcon:{type:String,computed:"_computeIcon(status)"},statusInfo:{type:String,computed:"_computeTooltip(status)"},status:{type:String,reflectToAttribute:!0}}}static get template(){return Polymer.html`
         <style include="shared-styles">
         :host(:not([hidden])) {
           display:block;
           padding-right: var(--spacing-m);
           width: 3em;
           text-align: center;
         }
         iron-icon {
           padding: var(--spacing-xs) 0px;
         }
         :host([status=approved]) iron-icon {
           color: var(--positive-green-text-color);
         }
         :host([status=pending]) iron-icon {
           color: #ffa62f;
         }
         :host([status=missing]) iron-icon,
         :host([status=error]) iron-icon {
           color: var(--negative-red-text-color);
         }
         </style>
         <gr-tooltip-content title="[[statusInfo]]" has-tooltip>
           <iron-icon icon="[[statusIcon]]"></iron-icon>
         </gr-tooltip-content>
       `}static get observers(){return["computeStatusIcon(model.status, path, oldPath, cleanlyMergedPaths, cleanlyMergedOldPaths)"]}loadPropertiesAfterModelChanged(){super.loadPropertiesAfterModelChanged(),this.modelLoader.loadStatus()}computeStatusIcon(e,t,s,o,n){if(console.log(e,t,s,o,n),void 0===e||[t,s].includes(void 0)&&void 0===o)return;const r=e.codeOwnerStatusMap,i=void 0===t?o:[t];console.log(i);const a=i.filter((e=>!A.includes(e))).map((e=>this._computeStatus(r.get(e))));console.log(a);const l=void 0===s?n:[s];console.log(l);const c=l.filter((e=>!A.includes(e)&&!!e)).map((e=>this._computeStatus(r.get(e),!0)));console.log(c),this.status=a.concat(c).reduce(((e,t)=>M.indexOf(e)<M.indexOf(t)?e:t)),console.log("final:",this.status)}_computeIcon(e){return R[e]}_computeTooltip(e){return k[e]}_computeStatus(e,t=!1){return void 0===e?t?L:I:e.status===p?t?F:x:e.status===w?t?E:P:T}}
/**
   * @license
   * Copyright (C) 2020 The Android Open Source Project
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
let D,G;function $(e){D=e,G&&(G.banner=e)}function j(e){G&&(G.banner&&(G.banner.pluginStatus=void 0),G.banner=void 0),G=e,G&&(G.banner=D)}customElements.define(B.is,B);class H extends Polymer.Element{static get is(){return"gr-code-owners-banner"}static get template(){return Polymer.html`
      <style include="shared-styles">
        :host {
          display: block;
          overflow: hidden;
          background: red;
        }
        .text {
          color: white;
          font-family: var(--header-font-family);
          font-size: var(--font-size-h3);
          font-weight: var(--font-weight-h3);
          line-height: var(--line-height-h3);
          margin-left: var(--spacing-l);
        }
      </style>
      <span class="text">[[_getErrorText(pluginStatus)]]</span>
      <gr-button link on-click="_showFailDetails">
        Details
      </gr-button>
    `}static get properties(){return{hidden:{type:Boolean,value:!0,reflectToAttribute:!0,computed:"_computeHidden(pluginStatus)"},pluginStatus:{type:Object}}}connectedCallback(){super.connectedCallback(),$(this)}disconnectedCallback(){super.disconnectedCallback(),$(void 0)}_computeHidden(e){return!e||!i(e.state)}_getErrorText(e){return e&&e.state!==r?"The code-owners plugin has configuration issue. Please contact the project owner or the host admin.":"Error: Code-owners plugin has failed"}_showFailDetails(){V(this,this.pluginStatus)}}customElements.define(H.is,H);class q extends(y(Polymer.Element)){static get is(){return"owners-plugin-status-notifier"}static get properties(){return{banner:{type:Object},_stateForFindOwnersPlugin:{type:Object,notify:!0,computed:"_getStateForFindOwners(model.pluginStatus, model.branchConfig, change)"}}}static get observers(){return["_pluginStatusOrBannerChanged(model.pluginStatus, banner)"]}connectedCallback(){super.connectedCallback(),j(this)}disconnectedCallback(){super.disconnectedCallback(),j(void 0)}_pluginStatusOrBannerChanged(e,t){t&&(t.pluginStatus=e)}_loadDataAfterStateChanged(){this.modelLoader.loadPluginStatus(),this.modelLoader.loadBranchConfig()}_getStateForFindOwners(e,t,s){return void 0===e||void 0===t||null==s?{branchState:"LOADING"}:i(e.state)?{change:s,branchState:"FAILED"}:{change:s,branchState:t.disabled?"DISABLED":"ENABLED"}}}function V(e,t){e.dispatchEvent(new CustomEvent("show-error",{detail:{message:t.failedMessage},composed:!0,bubbles:!0}))}
/**
   * @license
   * Copyright (C) 2020 The Android Open Source Project
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
   */customElements.define(q.is,q);class W extends(y(Polymer.Element)){static get is(){return"owner-requirement-value"}static get template(){return Polymer.html`
        <style include="shared-styles">
        :host {
          --gr-button: {
            padding: 0px;
          }
        }
        p.loading {
          display: flex;
          align-content: center;
          align-items: center;
          justify-content: center;
        }
        .loadingSpin {
          display: inline-block;
          margin-right: var(--spacing-m);
          width: 18px;
          height: 18px;
        }
        gr-button {
          padding-left: var(--spacing-m);
        }
        a {
          text-decoration: none;
        }
        </style>
        <p class="loading" hidden="[[!_isLoading]]">
          <span class="loadingSpin"></span>
          Loading status ...
        </p>
        <template is="dom-if" if="[[!_isLoading]]">
          <template is="dom-if" if="[[!_pluginFailed(model.pluginStatus)]]">
            <template is="dom-if" if="[[!model.branchConfig.no_code_owners_defined]]">
              <template is="dom-if" if="[[!_newerPatchsetUploaded]]">
                <span>[[_computeStatusText(_statusCount, _isOverriden)]]</span>
                <template is="dom-if" if="[[_overrideInfoUrl]]">
                  <a on-click="_reportDocClick" href="[[_overrideInfoUrl]]"
                    target="_blank">
                    <iron-icon icon="gr-icons:help-outline"
                      title="Documentation for overriding code owners"></iron-icon>
                  </a>
                </template>
                <gr-button link on-click="_openReplyDialog">
                  [[_getSuggestOwnersText(_statusCount)]]
                </gr-button>
              </template>
              <template is="dom-if" if="[[_newerPatchsetUploaded]]">
                <span>A newer patch set has been uploaded.</span>
              </template>
            </template>
            <template is="dom-if" if="[[model.branchConfig.no_code_owners_defined]]">
              <span>No code-owners file</span>
              <a href="https://gerrit.googlesource.com/plugins/code-owners/+/HEAD/resources/Documentation/user-guide.md#how-to-submit-changes-with-files-that-have-no-code-owners" target="_blank">
                <iron-icon icon="gr-icons:help-outline"
                  title="Documentation about submitting changes with files that have no code owners?"></iron-icon>
              </a>
            </template>
          </template>
          <template is="dom-if" if="[[_pluginFailed(model.pluginStatus)]]">
            <span>Code-owners plugin has failed</span>
            <gr-button link on-click="_showFailDetails">
              Details
            </gr-button>
          </template>
        </template>
      `}static get properties(){return{_statusCount:Object,_newerPatchsetUploaded:Boolean,_isLoading:{type:Boolean,computed:"_computeIsLoading(model.branchConfig, model.status, model.userRole, model.pluginStatus)"},_isOverriden:{type:Boolean,computed:"_computeIsOverriden(change, model.branchConfig)"},_overrideInfoUrl:{type:String,computed:"_computeOverrideInfoUrl(model.branchConfig)"}}}static get observers(){return["_onStatusChanged(model.status, model.userRole)"]}loadPropertiesAfterModelChanged(){super.loadPropertiesAfterModelChanged(),this.reporting.reportLifeCycle("owners-submit-requirement-summary-start"),this.modelLoader.loadBranchConfig(),this.modelLoader.loadStatus(),this.modelLoader.loadUserRole()}_computeIsLoading(e,t,s,o){return!(this._pluginFailed(o)||e&&t&&s)}_pluginFailed(e){return e&&i(e.state)}_onStatusChanged(e,t){if(!e||!t)return this._statusCount=void 0,void(this._newerPatchsetUploaded=void 0);this._statusCount=this._getStatusCount(e.rawStatuses),this._newerPatchsetUploaded=e.newerPatchsetUploaded,this.reporting.reportLifeCycle("owners-submit-requirement-summary-shown",{...this._statusCount,user_role:t})}_computeOverrideInfoUrl(e){return e&&e.general&&e.general.override_info_url?e.general.override_info_url:""}_computeIsOverriden(e,t){if(!e||!t||!t.override_approval)return!1;for(const s of t.override_approval){const t=s.label,o=Number(s.value);if(!isNaN(o)&&this.change.labels[t]&&(e.labels[t].all||[]).find((e=>Number(e.value)>=o)))return!0}return!1}_getSuggestOwnersText(e){return e&&0===e.missing?"Add owners":"Suggest owners"}_getStatusCount(e){return e.reduce(((e,t)=>{const s=t.old_path_status,o=t.new_path_status;return o&&this._isMissing(o.status)?e.missing++:o&&this._isPending(o.status)?e.pending++:s?this._isMissing(s.status)?e.missing++:this._isPending(s.status)&&e.pending++:e.approved++,e}),{missing:0,pending:0,approved:0})}_computeStatusText(e,t){if(void 0===e||void 0===t)return"";const s=[];return e.missing&&s.push(`${e.missing} missing`),e.pending&&s.push(`${e.pending} pending`),s.length||s.push(t?"Approved (Owners-Override)":"Approved"),s.join(", ")}_isMissing(e){return e===p}_isPending(e){return e===w}_openReplyDialog(){this.model.setShowSuggestions(!0),this.dispatchEvent(new CustomEvent("open-reply-dialog",{detail:{},composed:!0,bubbles:!0})),this.reporting.reportInteraction("suggest-owners-from-submit-requirement",{user_role:this.model.userRole})}_showFailDetails(){V(this,this.model.pluginStatus)}}customElements.define(W.is,W);
/**
   * @license
   * Copyright (C) 2020 The Android Open Source Project
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
class z extends(y(Polymer.Element)){static get is(){return"suggest-owners-trigger"}static get properties(){return{hidden:{type:Boolean,computed:"_computeHidden(model.pluginStatus, model.branchConfig)",reflectToAttribute:!0}}}static get template(){return Polymer.html`
        <style include="shared-styles">
          :host {
            display: flex;
          }
          a {
            text-decoration: none;
          }
          gr-button {
            --padding: var(--spacing-xs) var(--spacing-s);
          }
        </style>
        <gr-button
          link
          on-click="toggleControlContent"
          has-tooltip
          title="Suggest owners for your change"
        >
          [[computeButtonText(model.showSuggestions)]]
        </gr-button>
        <span>
          <a on-click="_reportBugClick" href="https://bugs.chromium.org/p/gerrit/issues/entry?template=code-owners-plugin" target="_blank">
            <iron-icon icon="gr-icons:bug" title="report a problem"></iron-icon>
          </a>
          <a on-click="_reportDocClick" href="https://gerrit.googlesource.com/plugins/code-owners/+/HEAD/resources/Documentation/how-to-use.md" target="_blank">
            <iron-icon icon="gr-icons:help-outline" title="read documentation"></iron-icon>
          </a>
        </span>
      `}loadPropertiesAfterModelChanged(){super.loadPropertiesAfterModelChanged(),this.modelLoader.loadUserRole(),this.modelLoader.loadPluginStatus(),this.modelLoader.loadAreAllFilesApproved(),this.modelLoader.loadBranchConfig()}_computeHidden(e,t){return void 0===e||void 0===t||!!t.no_code_owners_defined||e.state!==o}toggleControlContent(){this.model.setShowSuggestions(!this.model.showSuggestions),this.reporting.reportInteraction("toggle-suggest-owners",{expanded:this.expanded,user_role:this.model.userRole?this.model.userRole:"UNKNOWN"})}computeButtonText(e){return e?"Hide owners":"Suggest owners"}_reportDocClick(){this.reporting.reportInteraction("code-owners-doc-click")}_reportBugClick(){this.reporting.reportInteraction("code-owners-bug-click")}}customElements.define(z.is,z),
/**
   * @license
   * Copyright (C) 2020 The Android Open Source Project
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
window.__gerrit_code_owners_plugin={state:{branchState:"LOADING"},stateChanged:new EventTarget},Gerrit.install((e=>{const t=e.restApi(),s=e.reporting();e.registerCustomComponent("banner",H.is);const o=e=>{window.__gerrit_code_owners_plugin.state=e.detail.value,window.__gerrit_code_owners_plugin.stateChanged.dispatchEvent(new CustomEvent("state-changed"))};e.registerCustomComponent("change-view-integration",q.is).onAttached((e=>{e.restApi=t,e.reporting=s,e.addEventListener("_state-for-find-owners-plugin-changed",o)})).onDetached((e=>{e.removeEventListener("_state-for-find-owners-plugin-changed",o)})),e.registerDynamicCustomComponent("change-view-file-list-header-prepend",N.is).onAttached((e=>{e.restApi=t,e.reporting=s})),e.registerDynamicCustomComponent("change-view-file-list-content-prepend",B.is).onAttached((e=>{e.restApi=t,e.reporting=s})),e.registerCustomComponent("submit-requirement-item-code-owners",W.is,{slot:"value"}).onAttached((e=>{e.restApi=t,e.reporting=s})),e.registerCustomComponent("reply-reviewers",z.is,{slot:"right"}).onAttached((e=>{e.restApi=t,e.reporting=s})),e.registerCustomComponent("reply-reviewers",O.is,{slot:"below"}).onAttached((e=>{e.restApi=t,e.reporting=s}))}))}();