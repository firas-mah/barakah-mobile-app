package com.example.barakah.coach

import com.example.barakah.model.Block
import com.example.barakah.model.Category
import com.example.barakah.model.Kind
import com.example.barakah.model.Product
import java.time.DayOfWeek
import java.time.LocalDate

/** Daily advice, motivation and milestones for a Muslim indie hacker. */
object Coach {

    data class Tip(val title: String, val body: String)

    /** One tip per day, rotating. Indie hacking lessons mixed with deen reminders. */
    val TIPS = listOf(
        Tip("Ship ugly, ship fast", "Your first version should embarrass you a little. A launched MVP teaches you more in a week than a perfect app teaches you in a year."),
        Tip("Charge from day one", "Free users tell you what's nice. Paying users tell you what's valuable. Put a price on it, even a small one."),
        Tip("Distribution > product", "A good product nobody sees makes $0. Spend as much time getting users as building features."),
        Tip("Talk to 5 users this week", "Send 5 messages to real people with the problem you solve. Their exact words become your landing page."),
        Tip("Solve your own problem", "The easiest products to build and sell are the ones you'd pay for yourself. Write down 3 annoyances you had this week."),
        Tip("Build in public", "Post what you built today, the numbers, the mistakes. People follow a journey, not a logo."),
        Tip("One product at a time", "Small bets are good, scattered focus is not. Finish, launch, measure — then decide to double down or move on."),
        Tip("Set a deadline", "Give every project a launch date 2–4 weeks away and tell people about it. Deadlines kill perfectionism."),
        Tip("Kill features, not momentum", "If a feature takes more than 2 days and nobody asked for it, cut it."),
        Tip("Use boring tech", "Pick the stack you already know. Users don't care about your framework, they care about their problem."),
        Tip("Landing page first", "Before building, make a one-page site with a waitlist or a 'Buy' button. If nobody clicks, you saved weeks."),
        Tip("Reply to every user", "Early on, support is marketing. A fast, kind reply turns a user into a fan who tells others."),
        Tip("Measure what matters", "Track 3 numbers only: visitors, signups, paying users. Everything else is noise for now."),
        Tip("Copy the format, not the product", "Study products that make money. How do they price? Where do they get users? Apply the pattern to a new niche."),
        Tip("SEO is a slow compound", "One useful article or free tool per week brings traffic for years. Start now, thank yourself in 6 months."),
        Tip("Consistency beats intensity", "2 focused hours every day beat a 14-hour Saturday. Protect your deep work blocks like prayer times."),
        Tip("Your phone is the enemy of deep work", "During deep work: Do Not Disturb, phone in another room. One task, start to finish."),
        Tip("Finish the day with a plan", "Before sleeping, write tomorrow's ONE thing. You'll start the morning already knowing what to do."),
        Tip("Launch more than once", "Launch on X, then Reddit, then Product Hunt, then niche communities. Each one is a new chance."),
        Tip("Revenue is the only validation", "Likes and compliments are not validation. A stranger paying you is."),
        Tip("Price higher than you think", "Beginners underprice. If nobody ever says 'too expensive', you're too cheap."),
        Tip("Write down your failures", "Every failed launch is data. Note what didn't work so the next product is smarter."),
        Tip("Automate what repeats", "If you do something more than 3 times, write a script or a template for it."),
        Tip("Sleep is a business tool", "A tired brain writes bugs and bad copy. Protect your sleep the way you protect your code."),
        Tip("Compare yourself to yesterday", "Successful indie hackers took years. Look at your own last month, not someone else's highlight reel."),

        // Deen
        Tip("Effort is yours, results are Allah's", "\"And that there is not for man except that for which he strives.\" (An-Najm 53:39). Do your part fully, then trust."),
        Tip("Tie your camel", "The Prophet ﷺ said: tie your camel, then rely on Allah (Tirmidhi). Plan, work, then put your trust in Him."),
        Tip("With hardship comes ease", "\"Indeed, with hardship comes ease.\" (Ash-Sharh 94:6). A slow month is not the end of the story."),
        Tip("Excellence is worship", "Allah loves that when one of you does a job, he does it with excellence (Tabarani). Your code and your product count."),
        Tip("Honest trade is honoured", "The honest, trustworthy merchant will be with the prophets, the truthful and the martyrs (Tirmidhi). No fake reviews, no dark patterns."),
        Tip("Small and constant", "The most beloved deeds to Allah are those done consistently, even if small (Bukhari & Muslim)."),
        Tip("Barakah in the early morning", "The Prophet ﷺ prayed: O Allah, bless my nation in their early mornings (Abu Dawud). Your best work hours are after Fajr."),
        Tip("Keep your income halal", "No riba, no gambling mechanics, no deceptive ads. Halal rizq with barakah beats more money without it."),
        Tip("Give from what you earn", "Set a % of every revenue for sadaqa, even $1 from your first sale. Charity does not decrease wealth (Muslim).")
    )

    fun tipFor(date: LocalDate): Tip = TIPS[(date.toEpochDay() % TIPS.size).toInt()]

    /** The text of the notification sent when a block starts. */
    fun blockMessage(block: Block, focus: String, date: LocalDate): String = when {
        block.templateId == "deep1" ->
            if (focus.isNotBlank()) "Your ONE thing: $focus. Phone on DND, start now."
            else "Open the app and write your ONE thing first. Then phone on DND."
        block.category == Category.BUILD && block.templateId == "weekly_review" ->
            "Look at the numbers: users, revenue, posts, Quran. What will you ship next week?"
        block.category == Category.BUILD -> listOf(
            "Close X, close YouTube. One task, start to finish.",
            "Ship something small before this block ends.",
            "If you're stuck for 20 minutes, cut the scope.",
            "Write the code that a user will actually see today."
        )[(date.dayOfMonth + block.start.hour) % 4]
        block.category == Category.GROW -> listOf(
            "Reach 5 real people who have the problem you solve.",
            "Answer every user message before doing anything else.",
            "Write one useful post or article that brings people to your product.",
            "Find where your users hang out online and help them there."
        )[(date.dayOfMonth) % 4]
        block.category == Category.BRAND -> "Post today's progress (numbers, screenshot, lesson), then reply to 10 people in your niche."
        block.category == Category.LEARN -> "Learn one thing you'll use this week — not just watch, apply."
        block.kind == Kind.FRIENDS -> "Enjoy, and be home on time. Tomorrow's Fajr depends on tonight."
        block.templateId == "review" -> "Check what you did, write 3 wins, set tomorrow's ONE thing. Then adhkar and lights off."
        else -> ""
    }

    // ───────── Milestones ─────────
    data class Milestone(val emoji: String, val title: String, val reached: Boolean)

    fun milestones(products: List<Product>, followers: Int): List<Milestone> {
        val mrr = products.sumOf { it.mrr }
        val shipped = products.count { it.status.shipped }
        val users = products.sumOf { it.users }
        return listOf(
            Milestone("💡", "Write down your first product idea", products.isNotEmpty()),
            Milestone("🚀", "Launch your first product", shipped >= 1),
            Milestone("👤", "Get your first user", users >= 1),
            Milestone("💵", "Make your first dollar", mrr > 0),
            Milestone("📣", "Reach 100 followers", followers >= 100),
            Milestone("🔁", "Ship 3 products", shipped >= 3),
            Milestone("💯", "\$100 MRR", mrr >= 100),
            Milestone("🧑‍🤝‍🧑", "100 users", users >= 100),
            Milestone("📣", "Reach 1,000 followers", followers >= 1000),
            Milestone("🏆", "\$1,000 MRR", mrr >= 1000),
            Milestone("🔟", "Ship 10 products", shipped >= 10),
            Milestone("🌙", "\$10,000 MRR — financial freedom", mrr >= 10000)
        )
    }

    /** A warning shown on the Indie screen when something important is being neglected. */
    fun indieWarning(products: List<Product>, today: LocalDate): String? {
        if (products.isEmpty()) return "You have no product yet. Pick the smallest idea you can launch in 14 days."
        val launches = products.mapNotNull { runCatching { LocalDate.parse(it.launchedOn) }.getOrNull() }
        val last = launches.maxOrNull()
        return when {
            last == null -> "Nothing launched yet. Set a launch date for your current product and post it on X."
            last.isBefore(today.minusDays(45)) -> "Your last launch was over 45 days ago. Ship something this month — even a small tool."
            products.none { it.mrr > 0 } && products.any { it.status.shipped } -> "You've launched but no revenue yet. Add a price, or talk to 5 users this week."
            else -> null
        }
    }

    /** Extra line for specific days. */
    fun dayNote(date: LocalDate): String? = when (date.dayOfWeek) {
        DayOfWeek.SATURDAY -> "New week: what will you ship by Thursday?"
        DayOfWeek.THURSDAY -> "Last work day of the week: ship what you started."
        DayOfWeek.FRIDAY -> "Jumu'ah & rest day: ghusl, Kahf, salawat — recharge, be with family, review the week."
        else -> null
    }
}
