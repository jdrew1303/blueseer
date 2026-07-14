# BlueSeer User Guide

Task-based walkthroughs for the day-to-day flows in BlueSeer, written for
end users rather than administrators. Each page is self-contained, with
screenshots taken against a sample bakery dataset so you can see real data
in each screen.

Start here if you're new: **[00 — Getting Started](00-getting-started.md)**

## Guides

1. [Getting Started](00-getting-started.md) — logging in, navigating the
   menus, and conventions used throughout this guide.
2. [Adding a New Item](01-adding-a-new-item.md) — ingredients, packaging,
   and other item types.
3. [Creating a Recipe](02-creating-a-recipe.md) — building a BOM, adding
   components, and how the ingredient/nutrition rollup works.
4. [Producing a Batch and Printing Labels](03-producing-a-batch-and-labels.md)
   — reporting production against a batch/lot number and printing the
   ingredient/nutrition label.
5. [Doing a Recall (Lot Genealogy Lookup)](04-lot-recall.md) — tracing a
   bad supplier lot forward to customers, or a finished batch straight to
   its customers.
6. [Putting an Order Through](05-sales-orders.md) — sales orders, and how
   to make sure the batch/lot number ends up on the invoice.
7. [Doing a Credit Note](06-credit-notes.md) — debit/credit memos against
   a customer account.
8. [Doing a Purchase Order / Accepting Delivery of Goods](07-purchase-orders.md)
   — raising a PO and receiving goods with the supplier's lot number
   captured.
9. [Doing Stock Reports](08-stock-reports.md) — on-hand quantity, batch/
   expiry, valuation, and reorder reports.
10. [Exporting Sales to Your Accounting System](09-accounting-export.md) —
    posting transactions and pulling a GL export.
11. [Setting Up Customers and Vendors](10-customers-vendors.md) — the
    address-book records everything else depends on.

## How traceability fits together

Several guides above connect into one chain worth understanding as a
whole: a supplier lot is captured at [receiving](07-purchase-orders.md),
consumed into a [batch during production](03-producing-a-batch-and-labels.md),
and shipped to a customer via a [serialized order](05-sales-orders.md). If
something goes wrong with any link in that chain, [Lot Genealogy /
Recall Lookup](04-lot-recall.md) walks it in either direction — from the
supplier lot forward, or from the batch straight to its customers.
