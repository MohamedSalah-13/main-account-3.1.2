# The returns contract

What a return is allowed to be, why each rule exists, and what is still open. Read it before
touching anything under `features/returns`, `controller/invoice/DialogReturn*`,
`ReturnEntryCoordinator`, or the return half of `InvoiceSaveService`.

The area was rebuilt over 2026-08-17/18 and reviewed again on 2026-09-19/20. Sections 1-6 are the
contract; §7 records what the second review changed; §8 what is still not done.

## 1. A return is a reversal, not a new document

Every rule below follows from one sentence: **a return gives back exactly what a document took,
and nothing else.** It is not a sale at a negative price and not a fresh negotiation. So:

- its line price is the source line's price, not today's;
- its cost is the source line's cost, so `document_profit` gives back the profit the sale made;
- its share of every discount is the source's share, line and header alike;
- its quantity cannot exceed what the source line moved.

Where the system cannot check one of these - a return with no source invoice - it says so rather
than pretending, and the settings in §5 decide how far that is allowed to go.

## 2. What it does to the books

`DocumentLedgerEffect` is the single rule and returns are not a special case of it: the cash
column is what the treasury moved, in `DocumentType.cashDirection()`'s direction, and `net - paid`
is what reached the party's account, in `ledgerSign()`'s. It does **not** branch on `invoice_type`.

`V15__return_cash_split.sql` is the scar. The two account views and `treasury_balance` each decided
a deferred return for themselves and read its cash column with two opposite meanings - one as the
refund, one as the account credit. They were self-consistent under an undocumented convention, so
client books balanced, and a partial refund produced nonsense. `PartyLedgerViewAcceptanceTest` is
what holds the three views to the rule now.

`document_profit` signs a return negative - revenue, cost and profit - so anything can sum the two
families without knowing which is which.

## 3. What is refused, at save, inside the transaction

`ReturnGuard` and `ReturnCostResolver` run inside `InvoiceSaveService.persist`. In order:

| Refusal | Why |
|---|---|
| the source does not exist | nothing to reverse |
| the party differs from the source's | goods bought from one supplier cannot be returned to another: the one actually owed is never credited and another is credited for goods they never sent |
| a deferred return of a **cash** invoice | a cash invoice was settled in full; a return of it has an account balance of exactly zero to reverse, so crediting an account invents a debt |
| a line that names no source line, on a return that names an invoice | pick 9 of 10 from the invoice so they are locked at the sale price, add the tenth by barcode at any price you like. `ReturnGuard` passes it - 9 + 1 against 10 sold is in order - and only the price was wrong |
| a price, unit or line-discount share different from the sale's | refunding less is as wrong as refunding more, only quieter: it keeps part of the money on goods now back on the shelf, with no record of the difference |
| more of an item than the source sold, less what other returns took | the original quantity rule |
| more of a **line** than that line sold | an invoice listing one item twice - five at 100 and five at 60 - let all ten come back against the line at 100: ten of ten sold, every price matching, and 200 refunded that nobody paid |
| a header discount that is not the source's proportional share | §4 |
| the source document being deleted while a return names it | `ReturnLinkGuard`. Deleting only the sale removes the stock-out and leaves the stock-in, so every returned item's balance rises out of nothing |

**The source is locked** (`SELECT … FOR UPDATE`) before "how much is left" is read, so two tills
returning the same invoice cannot both pass. Until that lock existed it was prevented only by
accident - `InvoiceStockGuard` happens to lock the item rows first - and any reordering of the two
guards would have opened it again silently.

**Editing a saved return is the same path.** `ReturnEntryCoordinator.restoreSource` puts the stored
source back before any guard runs; without it `ReturnGuard` reads a source of `0`, treats the whole
document as a free return it has nothing to compare against, and every check above is off. The
return's own stored quantities are excluded from "already returned", or re-saving one would find
itself in the way.

## 4. Discounts

Two kinds, and both belong to the return in proportion:

- **a line's own discount** covers that line, so returning 2 of 5 refunds two fifths of it
  (`ReturnableLineSelection.discountShareFor`, checked by `ReturnCostResolver`);
- **the document's own discount** - the "additional discount" box - is shared *by value*:
  the fraction of the source's `total` that the return's `total` is (`ReturnHeaderDiscount`).
  A document discount has no owner among the lines, and any other allocation would be invented.

Nothing carried the second one until 2026-09-19. A sale of 1000 with 100 off was paid 900, and
returning all of it from the picker refunded 1000 - the lines came back at their own prices and the
return's discount box stayed at zero. The 100 left the till, or was credited to the customer, out of
nothing, and the return showed a bigger loss than the sale had shown profit.

The screen fills the box and **locks** it while a source is named, for the reason a picked line's
price is locked: a figure the save will accept no other value for is not a figure to offer for
typing. The share stops at the whole source discount, so a return larger than its source (the source
was edited down afterwards) cannot give back more than was taken.

Editing a picked line's quantity recomputes its share from the **source line**, not by scaling the
rounded share already on the row - a rounded share scaled up lands a piastre off what the save
expects, which is a save nobody can complete.

## 5. A return with no source invoice

Allowed, and always was: the customer who lost the receipt is a real case. Two settings narrow it,
both **off** by default so no install changes behaviour on upgrade (Settings → checks):

- `return.require.source.invoice` refuses one outright;
- `return.free.limit` caps what one may be worth, judged on the goods (quantity × price less the
  line discounts) rather than the document net - it is a ceiling on what leaves the shelf.

Saving one always asks for confirmation, and that confirmation is where its **reason** is asked for:
the reason combo lives in the picker, which a free return never opens, so before 2026-09-20 every
free return reached the reasons report as "none given" however deliberate the person had been.

## 6. Two warnings, and why they are not refusals

`ReturnSettlementAdvice`, in the shape `ExpenseBalanceCheck` established. Both cases are legitimate
entries that are usually a mistake, and the person at the counter is the one who knows which:

- **a deferred free return on the party cash sales land on.** That party is a bucket, not a person:
  nobody is billed for what it owes or paid what it is owed, so the credit sits there for ever. The
  development database carries -500 of exactly this. Only for a return naming *no* invoice - one
  that names a deferred sale to that party is reducing a balance rather than inventing one.
  Which party it is comes from the default-customer setting, never a literal `1`.
- **cash handed back to a party who still owes.** Settling on account instead would reduce the debt
  and move no money. The balance is read best-effort: the party screens are permission-guarded, and
  a cashier without that permission gets no warning rather than a failed save.

The user chose warnings over refusals on 2026-09-19, and chose to leave deferred *sales* on the cash
party alone - the same uncollectable balance, but outside the returns review's scope.

## 7. What the 2026-09-19/20 review changed

Everything in §3's last two rows, §4, §5's reason, §6, and:

- **the source is found, not recited.** It was a `TextInputDialog` taking a number, so a customer
  who had lost their copy could not be served unless somebody went and found it first - and the one
  thing the counter always has is the name. `ReturnSourceSearch` reads digits as a document number
  and the rest as a party name, either or both; a document already fully returned is listed and
  marked rather than hidden, because "it has all come back" is the answer being looked for.
- **the picker reads ٠-٩.** It parsed quantities with `Double.parseDouble`, which does not know the
  digits an Arabic keyboard produces, so a quantity typed the ordinary way was silently a zero and
  the line silently left off the return (`ReturnQuantityInput`). It also gained a price column,
  "return all", and clamping to what is left.
- **`lineById` is asked for the line *of the named document***, and a row's item must be its line's
  item. Nothing on the screen produces either mismatch; a price and a cost read from another item's
  line are wrong in a way every later check would pass.
- **a refusal names the item, not its id.** "الصنف رقم 2" reached somebody holding the item itself;
  `ReturnEligibility.LineQuantity` carries the name and falls back to the id only where there is
  none.
- **`ReturnPolicy` is read at every save**, not once when the save service is built. Turning "a
  return must name an invoice" on had no effect until every open invoice window had been closed and
  reopened - a setting that appears to do nothing is worse than one that is not there. The guard
  takes a `Supplier<ReturnPolicy>`.
- **deleting a document warns when it would take the shelf below zero** (`DocumentDeleteStockCheck`).
  Deleting reverses the stock effect, so a purchase or a sales return takes goods back out; if they
  have since been sold the balance lands under zero, found weeks later at a count. A warning and not
  a refusal, because a shop that sells before entering the supplier's bill is already below zero on
  paper and refusing would block the very correction that puts it right.

**Watched on screen, 2026-09-20**, and the screen found what 2,948 green tests could not: the delete
warning asked `JdbcInvoiceStockRepository` for the balance - rightly, it is the one definition there
is of it - from the JavaFX thread with no transaction open, and that class refuses to work outside
one because its own reads take `FOR UPDATE` locks. So pressing delete produced a reference-code error
instead of a warning, which reads as a refusal: a courtesy feature frightening somebody out of a
legitimate delete is worse than no feature. It runs inside the service's own `transaction.execute`
now, and a failure to read is **logged and passed over** rather than shown. This is the trap
`JdbcReturnableRepository`'s javadoc already records - a `requireTransaction` guard copied from that
same class once broke every read-only caller of the returns repository.

What else the run showed working: the source found by an Arabic name and by ٠-٩ digits, an unmatched
name giving an empty list, the cash-refund warning with the right figure (900.00) and cancelling it
writing nothing, a free return refused while `return.require.source.invoice` was on, **the same
screen accepting it once the setting was turned off** - which is the policy-per-save fix - the reason
prompt storing `DAMAGED` against a NULL source, and the delete warning naming the item and the
warehouse and predicting `-14`, which is exactly where the balance landed once it was confirmed.

**Proven against MySQL** on a schema migrated from nothing to V73: `ReturnableRepositoryAcceptanceTest`
and `ReturnSourceAcceptanceTest`, 13 cases, twice, no residue. **Watched on screen** on that schema:
the header-discount share filling and following the quantity, the per-line cap refusing with nothing
written and no number burnt, ٣ read as 3, a picked line's discount share following an edit, a deferred
return crediting exactly the net and moving no till, a purchase return taking cash *in*, and a saved
return reopened with its discount box read-only and `excludingReturnId` letting 10 become 20.

## 8. What is still open

- **The till is never checked.** `InvoiceSaveService` does no treasury-balance check at all, so a
  cash refund leaves a drawer that may be empty. This is true of every cash document, not returns
  alone - a cash purchase overdraws the same way - so it is a decision about the save path rather
  than about returns.
- **`InvoiceSaveService` has ten telescoping constructors**, each added by one more collaborator.
  A builder is the answer; it was not done here because it is a large mechanical diff over a
  critical class with no behaviour change, and worth its own change.
- **Arabic literals** remain in `InvoicePaymentTerms` and `InvoiceLineEditService`, where a service
  should throw a message key.
- **The reasons report and the returned-status badge have never been watched on screen**, nor the
  warning about a deferred free return on the cash party. This used to say the party could not be
  changed at all, because the field looked inert on a scratch schema holding one customer - and a
  search with no match hides its list rather than saying so. **That was wrong**: the field is a
  `PartySuggestionField`, typable and searched as you type, installed for all four families with
  nothing disabling it, and the button beside it is the pin every other header control has. Watched
  on 2026-09-20 on a copy of a real database: typing "ام" listed eight customers and Enter chose one.
  So nothing blocks that warning being seen; it simply has not been.
- **Returns written before 2026-08-18 carry no `source_line_id`**, so the per-line rules cannot
  apply to them; the per-item rules still do.
