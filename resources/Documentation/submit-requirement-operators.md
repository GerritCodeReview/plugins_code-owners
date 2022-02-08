# Submit Requirement Operators

The @PLUGIN@ plugin contributes the following operators. These operators can
only be used in submit requirements expressions and cannot be used in search:

 * **has:enabled_code-owners**

   Matches with changes that have the code-owners functionality enabled. For
   example, if code-owners is disabled for a specific branch, changes in this
   branch will not be matched against this operator.

 * **has:approval_code-owners**

   Matches with changes that have all necessary code-owner approvals or a
   code-owner override. This operator does not match with closed (merged)
   changes.

---

Back to [@PLUGIN@ documentation index](index.md)

Part of [Gerrit Code Review](../../../Documentation/index.md)
