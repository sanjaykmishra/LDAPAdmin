# TAM and Revenue Model Evolution by Phase

## Starting Point (Today)

| Metric | Value | Basis |
|---|---|---|
| Orgs running significant LDAP | ~50,000 worldwide | |
| Need governance + would pay | 5-10% | ~2,500-5,000 orgs |
| Average deal size | $7,000/yr | |
| **Total addressable market** | **$17M-35M** | |
| Realistic capture (2-5%) | $350K-1.75M/yr | |
| Win rate against spreadsheets | Low (~5%) | No wizard, no docs, CSV-only |
| Annual churn | High (~40%) | No automation, manual everything |

The problem isn't market size. It's that LDAPAdmin loses deals it should win because prospects can't get to value fast enough, and customers who do adopt churn because the workflow stays manual.

---

## After Phase 1: Buyable (Weeks 1-4)

**What changes:** Setup wizard, quick-start guide, PDF reports, group picker, SIEM export.

The market doesn't get bigger. The conversion rate does.

| Metric | Before | After | Why |
|---|---|---|---|
| Addressable orgs | 2,500-5,000 | 2,500-5,000 | Same market, same need |
| Win rate | ~5% | ~15-20% | 30-minute time-to-value kills the "we'll just use spreadsheets" objection. PDF reports make the value tangible in a demo. |
| Average deal size | $7,000 | $7,000 | No new capabilities to charge more for |
| **Realistic annual revenue** | **$350K-1.75M** | **$1M-5M** | 3-4x improvement from conversion alone |
| Churn | ~40% | ~35% | Slightly better — easier to use, but still manual |

**The insight:** Phase 1 is not a revenue-per-customer play. It's a volume play. The product is already capable enough — it just loses every race to first impression. A setup wizard and a PDF report sound boring but they're worth 3-4x revenue because they convert tire-kickers into paying customers.

---

## After Phase 2: Sticky (Weeks 5-10)

**What changes:** SoD engine, compliance dashboard, scheduled reviews, evidence export, templates.

Two things shift: price goes up and churn goes down.

| Metric | Phase 1 | Phase 2 | Why |
|---|---|---|---|
| Addressable orgs | 2,500-5,000 | 3,000-6,000 | SoD and evidence export attract orgs that previously ruled LDAPAdmin out as "not serious enough" |
| Win rate | 15-20% | 20-25% | Evidence package is a killer demo moment — one click and the auditor has everything |
| Average deal size | $7,000 | $10,000-12,000 | SoD + evidence export justifies 40-70% premium. These are features customers currently pay SailPoint $50K+ for |
| Annual churn | ~35% | ~15-20% | Automated campaigns + dashboard = daily/weekly usage. Hard to go back to spreadsheets once campaigns run themselves |
| **Realistic annual revenue** | **$1M-5M** | **$3M-10M** | Higher price x better conversion x lower churn compounds fast |

**The insight:** Phase 2 is where unit economics flip. Customer lifetime value roughly doubles (higher price, half the churn). This is where the business becomes self-sustaining. A customer paying $10K/yr with 80% retention has an LTV of ~$50K. At $7K/yr with 60% retention, LTV is ~$17.5K. That 3x LTV difference funds everything that comes after.

### New Pricing After Phase 2

| Tier | Users | Annual Price |
|---|---|---|
| Small | < 500 | $3,000-5,000/yr |
| Mid | 500-2,000 | $7,000-12,000/yr |
| Mid-Large | 2,000-5,000 | $12,000-20,000/yr |
| Large | 5,000-10,000 | $20,000-35,000/yr |

---

## After Phase 3: Indispensable (Weeks 11-18)

**What changes:** BambooHR integration, orphaned account detection, Entra ID connector, reviewer context, cross-campaign reporting.

This is the phase where the market itself gets bigger.

| Metric | Phase 2 | Phase 3 | Why |
|---|---|---|---|
| **Addressable orgs** | **3,000-6,000** | **15,000-30,000** | Three market expansions stack: (1) HR integration attracts orgs that need lifecycle, not just reviews. (2) Entra ID attracts hybrid shops — 5-10x the pure-LDAP market. (3) Orphaned account detection attracts orgs that haven't failed an audit yet but want to prevent it. |
| Win rate | 20-25% | 20-25% | Holds steady — bigger market means more competition too |
| Average deal size | $10K-12K | $14K-18K | HR integration + multi-directory governance justifies premium. "Identity lifecycle platform" prices differently than "LDAP admin tool" |
| Annual churn | 15-20% | 10-15% | HR integration creates operational dependency. If LDAPAdmin is disabling accounts when people leave, ripping it out is a security risk |
| **Realistic annual revenue** | **$3M-10M** | **$8M-25M** | Market expansion is the multiplier |

### Market Expansion Breakdown

| New Segment | Size | Why They Buy |
|---|---|---|
| Pure LDAP shops (original) | 2,500-5,000 | Access reviews + SoD (same as before) |
| Hybrid LDAP + Entra ID shops | 8,000-15,000 | Unified governance across on-prem and cloud directory |
| Orgs buying for lifecycle automation | 3,000-8,000 | HR-driven joiner/mover/leaver solves a problem they currently solve with manual tickets or nothing |
| Preventive buyers (no audit failure yet) | 2,000-5,000 | Orphaned account detection is proactive — they buy before the auditor forces them to |

**Total: ~15,000-30,000 addressable orgs** — roughly 5x the Phase 1 market.

### New Pricing After Phase 3

| Tier | Users | Annual Price |
|---|---|---|
| Small | < 500 | $4,000-6,000/yr |
| Mid | 500-2,000 | $10,000-16,000/yr |
| Mid-Large | 2,000-5,000 | $16,000-28,000/yr |
| Large | 5,000-10,000 | $28,000-45,000/yr |

Per-identity pricing shifts to $3-5/identity/year.

---

## After Phase 4: Defensible (Weeks 19-26)

**What changes:** Google Workspace, ticketing, more HR connectors, JML automation, API docs.

Phase 4 doesn't dramatically expand the market. It deepens penetration and locks customers in.

| Metric | Phase 3 | Phase 4 | Why |
|---|---|---|---|
| Addressable orgs | 15,000-30,000 | 18,000-35,000 | Marginal expansion from Google Workspace shops and ServiceNow-dependent orgs |
| Win rate | 20-25% | 25-30% | API docs unlock enterprise procurement. Ticketing integration removes "doesn't fit our workflow" objection |
| Average deal size | $14K-18K | $16K-22K | More connectors = more value per seat. JML automation is a premium feature |
| Annual churn | 10-15% | 8-12% | Ticketing integration + automated JML workflows create deep operational dependency. Replacing LDAPAdmin now means rebuilding approval routing, lifecycle triggers, and audit trails |
| **Realistic annual revenue** | **$8M-25M** | **$12M-35M** | Incremental, not transformative — but with much better margins |

---

## The Compound Effect

Here's what the full trajectory looks like:

```
                    Addressable    Avg Deal    Churn    Realistic
                    Orgs           Size                 Annual Rev
─────────────────────────────────────────────────────────────────
Today               2,500-5,000    $7K         ~40%     $350K-1.75M
Phase 1 (Week 4)    2,500-5,000    $7K         ~35%     $1M-5M
Phase 2 (Week 10)   3,000-6,000    $10K-12K    ~18%     $3M-10M
Phase 3 (Week 18)   15,000-30,000  $14K-18K    ~12%     $8M-25M
Phase 4 (Week 26)   18,000-35,000  $16K-22K    ~10%     $12M-35M
```

**Each phase pulls a different lever:**

| Phase | Primary Lever | Revenue Multiplier |
|---|---|---|
| Phase 1 | Conversion rate (3-4x) | 3-4x |
| Phase 2 | Price + retention (1.5x price, 2x LTV) | 2-3x |
| Phase 3 | Market size (5x addressable orgs) | 2.5-3x |
| Phase 4 | Lock-in + win rate (incremental) | 1.3-1.5x |

**Cumulative: ~35-50x revenue from today to Phase 4 completion.**

---

## Revenue Model Evolution

The pricing model itself should evolve:

**Today:** Flat annual license (if it were commercial) — simple, but leaves money on the table.

**After Phase 2:** Per-identity/year pricing at $3-5/identity. This scales with the customer and aligns incentives (more users governed = more value delivered = more revenue).

**After Phase 3:** Tiered per-identity + connector add-ons:
- Base: $2-3/identity/year (LDAP governance)
- HR connector: +$1/identity/year
- Entra ID connector: +$1/identity/year
- Evidence package: included in base (drives conversion)

**After Phase 4:** Platform pricing:
- Starter (1 directory, no HR): $2-3/identity/year
- Professional (multi-directory + HR): $4-6/identity/year
- Enterprise (all connectors + JML + ticketing + API): $6-8/identity/year

### Example Customer at 2,000 Identities

| Phase | Plan | Annual Revenue |
|---|---|---|
| Phase 1 | Flat license | $7,000 |
| Phase 2 | Per-identity base | $8,000 |
| Phase 3 | Base + HR + Entra | $10,000 |
| Phase 4 | Enterprise tier | $14,000 |

That's a 2x expansion within a single account — without adding a single user.

---

## Honest Caveats

**1. These numbers assume execution, not just features.** Shipping code is necessary but not sufficient. You also need: a website that positions the product correctly, a trial experience that converts, and at least one case study from a real customer.

**2. The Phase 3 market expansion is the riskiest bet.** Pure LDAP shops are a known market. Hybrid shops "migrating to cloud" might decide they don't need LDAP governance at all — they might just finish the migration. The HR integration de-risks this by making LDAPAdmin valuable regardless of where the directory lives.

**3. $12M-35M annual revenue requires a sales motion.** Self-serve can carry you to $1-3M. Beyond that, mid-size compliance buyers expect demos, POCs, and someone to answer the phone. The product roadmap doesn't account for go-to-market costs.

**4. The "not venture scale" caveat still applies, but differently.** At $35M ARR the business is extremely valuable — but it took 26 weeks of product work plus years of sales execution to get there. A VC wants $100M+ ARR potential. A bootstrapped founder or a small PE-backed team would find $12-35M ARR life-changing.

---

## Bottom Line

The roadmap turns a ~$1M revenue opportunity into a ~$12-35M one. Not by building a different product, but by:

1. Making what exists usable (Phase 1: 3-4x from conversion)
2. Making it worth more per customer (Phase 2: 2-3x from price + retention)
3. Selling it to a much larger market (Phase 3: 2.5-3x from market expansion)
4. Making it hard to leave (Phase 4: 1.3-1.5x from lock-in)

The most expensive mistake would be jumping to Phase 3 (integrations) before Phase 1 (usability). You can't expand the market if you can't convert the market you already have.
