package io.github.swashbuckler104.budgetwidget.ingest.feedback

/**
 * The only words [SmsRedactor] lets through (ADR-0046): banking terms, payment rails, banks and
 * card networks, months, weekdays and common function words. Seeded from the words that recur
 * across the golden corpus, docs/handoff/probe-sms.tsv and upstream's test SMS, then reviewed by
 * hand. Deliberately absent: person names, places and merchants (shops, apps, brands), even ones
 * that look harmless, because a merchant says where someone spends. Lowercase; matching ignores
 * case.
 *
 * Adding a word: it must be useful for reading a bank SMS template and must not identify anyone or
 * anywhere. When in doubt, leave it out: a masked word costs the parser work little.
 */
internal val RedactorVocabulary: Set<String> = setOf(
    // Function words.
    "a", "about", "above", "after", "against", "all", "also", "am", "an", "and", "any", "are", "as", "at",
    "be", "been", "before", "being", "below", "between", "both", "but", "by", "can", "could", "did", "do",
    "does", "done", "each", "else", "for", "from", "has", "have", "having", "here", "how", "if", "in",
    "into", "is", "it", "its", "kindly", "last", "less", "may", "more", "must", "new", "next", "not",
    "now", "of", "off", "on", "once", "only", "or", "other", "our", "out", "over", "own", "per", "please",
    "same", "self", "shall", "should", "so", "than", "thank", "thanks", "that", "the", "then", "there",
    "these", "this", "those", "through", "thru", "till", "to", "today", "under", "until", "up", "upto",
    "us", "via", "was", "we", "were", "what", "when", "which", "who", "will", "with", "within", "would",
    "yes", "you", "your", "yours",
    // Accounts and cards.
    "ac", "acc", "acct", "account", "accounts", "atm", "bank", "banking", "card", "cards", "cardholder",
    "cc", "chq", "cheque", "classic", "contactless", "cr", "credit", "credited", "current", "customer",
    "dc", "dear", "debit", "debitcard", "debited", "deposit", "deposited", "deposits", "digits", "dr",
    "ecom", "end", "ending", "fd", "fixed", "holder", "joint", "lite", "member", "mini", "no", "nri",
    "num", "number", "od", "overdraft", "platinum", "pos", "prepaid", "primary", "rd", "recurring",
    "salary", "saving", "savings", "signature", "virtual", "wallet",
    // Money and movement.
    "amnt", "amount", "amt", "available", "aval", "avbl", "avl", "avlbal", "avlbl", "bal", "balance",
    "bill", "billed", "bills", "cash", "cashback", "charge", "charged", "charges", "deducted", "due",
    "emi", "emis", "fee", "fees", "fine", "gst", "inr", "interest", "limit", "lmt", "min", "minimum",
    "outstanding", "paid", "paise", "pay", "payable", "payee", "payer", "paying", "payment", "payments",
    "penalty", "points", "principal", "purchase", "purchased", "receive", "received", "receiving",
    "refund", "refunded", "reversal", "reversed", "reward", "rewards", "rs", "rupee", "rupees", "send",
    "sent", "spend", "spent", "tax", "tds", "total", "transfer", "transferred", "transfers", "usd",
    "value", "withdraw", "withdrawal", "withdrawn",
    // Transactions and references.
    "ach", "alert", "approved", "auto", "autopay", "beneficiary", "clearing", "clr", "collect", "completed",
    "confirmation", "date", "dated", "detail", "details", "ecs", "id", "imps", "info", "initiated", "linked",
    "mandate", "merchant", "nach", "neft", "pending", "processed", "ref", "reference", "registered", "request",
    "requested", "rrn", "rtgs", "si", "status", "success", "successful", "successfully", "towards", "tran",
    "transaction", "transactions", "trf", "trxn", "txn", "txns", "umn", "upi", "utr", "vpa",
    // Loans, investments and statements.
    "disbursal", "disbursed", "folio", "fund", "funds", "insurance", "investment", "lien", "loan", "loans",
    "mutual", "nav", "policy", "premium", "renewal", "repayment", "sanctioned", "sip", "statement", "units",
    // Failures, warnings and help.
    "already", "block", "blocked", "bounced", "call", "care", "code", "declined", "dispute", "failed",
    "failure", "forward", "fraud", "helpline", "ignore", "immediately", "insufficient", "issue", "login",
    "maintain", "never", "otp", "report", "secure", "service", "share", "sms", "sufficient", "unblock",
    "update", "updated", "valid", "visit",
    // Channels and placeholders.
    "app", "click", "download", "email", "link", "mail", "mob", "mobile", "net", "netbanking", "online",
    "ph", "tel", "url", "www", "com", "http", "https",
    // Time.
    "hrs", "ist", "pm", "time",
    "jan", "january", "feb", "february", "mar", "march", "apr", "april", "jun", "june", "jul", "july",
    "aug", "august", "sep", "sept", "september", "oct", "october", "nov", "november", "dec", "december",
    "mon", "monday", "tue", "tues", "tuesday", "wed", "wednesday", "thu", "thur", "thurs", "thursday",
    "fri", "friday", "sat", "saturday", "sun", "sunday",
    // Banks, payment banks and card issuers (the sender ID names the bank anyway).
    "au", "axis", "axisbank", "bandhan", "baroda", "bob", "boi", "canara", "canarabank", "catholic",
    "central", "chartered", "citi", "citibank", "city", "co", "cooperative", "cosmos", "csb", "cub", "dbs",
    "dhanlaxmi", "equitas", "esaf", "federal", "fi", "finance", "fino", "first", "gramin", "hdfc", "hdfcbk",
    "hsbc", "icici", "idbi", "idfc", "india", "indian", "indusind", "iob", "jana", "jupiter", "karnataka",
    "karur", "kotak", "kvb", "limited", "ltd", "mahabank", "mahindra", "maharashtra", "mercantile",
    "national", "niyo", "nsdl", "onecard", "overseas", "pnb", "psb", "punjab", "pvt", "rbl",
    "saraswat", "sbi", "sbicard", "sbm", "sc", "scapia", "sib", "sind", "slice", "small", "south",
    "southern", "standard", "state", "suryoday", "syrian", "tamilnad", "tmb", "uco", "ujjivan", "uni",
    "union", "utkarsh", "vysya",
    // Card networks and rails.
    "amex", "diners", "maestro", "master", "mastercard", "npci", "rupay", "visa",
)
