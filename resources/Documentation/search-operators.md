# Search Operators

Gerrit supports a query language to search for different changes. For a preview
of all core search operators click
[here](https://gerrit-review.googlesource.com/Documentation/user-search.html).

The code owners plugin contributes the following search operators:

 * **has:enabled_code-owners**
 
   Matches with changes that have the code-owners functionality enabled. For
   example, if code-owners is disabled for a specific branch, changes in this
   branch will not be matched against this operator.

 * **has:approval_code-owners**
   
   Matches with changes that have all necessary code-owner approvals or a
   code-owner override. This operator does not match with closed (merged)
   changes.

---

Back to [@PLUGIN@ documentation index](index.html)

Part of [Gerrit Code Review](../../../Documentation/index.html)
