package io.github.swashbuckler104.budgetwidget.core.designsystem.component

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Assignment
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AttachMoney
import androidx.compose.material.icons.rounded.Autorenew
import androidx.compose.material.icons.rounded.Brush
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CandlestickChart
import androidx.compose.material.icons.rounded.CardGiftcard
import androidx.compose.material.icons.rounded.Checkroom
import androidx.compose.material.icons.rounded.ChildCare
import androidx.compose.material.icons.rounded.Coffee
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.ConfirmationNumber
import androidx.compose.material.icons.rounded.Construction
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.Cottage
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.DeliveryDining
import androidx.compose.material.icons.rounded.DinnerDining
import androidx.compose.material.icons.rounded.DirectionsBus
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Draw
import androidx.compose.material.icons.rounded.FamilyRestroom
import androidx.compose.material.icons.rounded.Fastfood
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Flight
import androidx.compose.material.icons.rounded.Handshake
import androidx.compose.material.icons.rounded.Handyman
import androidx.compose.material.icons.rounded.HolidayVillage
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.HomeRepairService
import androidx.compose.material.icons.rounded.Hotel
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.LocalGasStation
import androidx.compose.material.icons.rounded.LocalHospital
import androidx.compose.material.icons.rounded.LocalParking
import androidx.compose.material.icons.rounded.LocalTaxi
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.Medication
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.Pets
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material.icons.rounded.ShoppingCart
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.Spa
import androidx.compose.material.icons.rounded.SportsBar
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.SportsSoccer
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Train
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.Vaccines
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.Work
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Line icons for category emoji (ADR-0052). A category still stores one emoji (backups, widgets
 * and notifications use it as text); in the app a known emoji is drawn as its icon, in the
 * category's color, and any other emoji is drawn as itself.
 */
object CategoryIcons {
    private val byEmoji: Map<String, ImageVector> = mapOf(
        "🍽" to Icons.Rounded.Restaurant,
        "🛒" to Icons.Rounded.ShoppingCart,
        "🍝" to Icons.Rounded.DinnerDining,
        "🍔" to Icons.Rounded.Fastfood,
        "🛵" to Icons.Rounded.DeliveryDining,
        "☕" to Icons.Rounded.Coffee,
        "🍺" to Icons.Rounded.SportsBar,
        "🚗" to Icons.Rounded.DirectionsCar,
        "⛽" to Icons.Rounded.LocalGasStation,
        "🚕" to Icons.Rounded.LocalTaxi,
        "🚌" to Icons.Rounded.DirectionsBus,
        "🅿" to Icons.Rounded.LocalParking,
        "🛍" to Icons.Rounded.ShoppingBag,
        "👕" to Icons.Rounded.Checkroom,
        "👗" to Icons.Rounded.Checkroom,
        "💻" to Icons.Rounded.Computer,
        "🏠" to Icons.Rounded.Home,
        "🏡" to Icons.Rounded.Cottage,
        "🏘" to Icons.Rounded.HolidayVillage,
        "📦" to Icons.Rounded.Inventory2,
        "📃" to Icons.AutoMirrored.Rounded.ReceiptLong,
        "📋" to Icons.AutoMirrored.Rounded.Assignment,
        "💡" to Icons.Rounded.Lightbulb,
        "📶" to Icons.Rounded.Wifi,
        "🌐" to Icons.Rounded.Language,
        "🔑" to Icons.Rounded.Key,
        "🚰" to Icons.Rounded.WaterDrop,
        "📺" to Icons.Rounded.Tv,
        "🏥" to Icons.Rounded.LocalHospital,
        "💊" to Icons.Rounded.Medication,
        "💉" to Icons.Rounded.Vaccines,
        "🛡" to Icons.Rounded.Shield,
        "🎓" to Icons.Rounded.School,
        "📚" to Icons.AutoMirrored.Rounded.MenuBook,
        "🎬" to Icons.Rounded.Movie,
        "🎟" to Icons.Rounded.ConfirmationNumber,
        "🎮" to Icons.Rounded.SportsEsports,
        "🎵" to Icons.Rounded.MusicNote,
        "⚽" to Icons.Rounded.SportsSoccer,
        "🏋" to Icons.Rounded.FitnessCenter,
        "👪" to Icons.Rounded.FamilyRestroom,
        "🎁" to Icons.Rounded.CardGiftcard,
        "💇" to Icons.Rounded.ContentCut,
        "💄" to Icons.Rounded.Brush,
        "💆" to Icons.Rounded.Spa,
        "👶" to Icons.Rounded.ChildCare,
        "🐶" to Icons.Rounded.Pets,
        "✈" to Icons.Rounded.Flight,
        "🏨" to Icons.Rounded.Hotel,
        "🚆" to Icons.Rounded.Train,
        "🗺" to Icons.Rounded.Map,
        "🏦" to Icons.Rounded.AccountBalance,
        "🏛" to Icons.Rounded.AccountBalance,
        "📈" to Icons.AutoMirrored.Rounded.TrendingUp,
        "💹" to Icons.Rounded.CandlestickChart,
        "🔁" to Icons.Rounded.Autorenew,
        "↔" to Icons.Rounded.SwapHoriz,
        "🤝" to Icons.Rounded.Handshake,
        "💰" to Icons.Rounded.Savings,
        "💼" to Icons.Rounded.Work,
        "💸" to Icons.Rounded.Payments,
        "💵" to Icons.Rounded.AttachMoney,
        "💳" to Icons.Rounded.CreditCard,
        "📌" to Icons.Rounded.PushPin,
        "📞" to Icons.Rounded.Call,
        "📱" to Icons.Rounded.Smartphone,
        "📥" to Icons.Rounded.Inbox,
        "🔧" to Icons.Rounded.Build,
        "🔨" to Icons.Rounded.Handyman,
        "🔩" to Icons.Rounded.Construction,
        "🛠" to Icons.Rounded.HomeRepairService,
        "⚠" to Icons.Rounded.WarningAmber,
        "✍" to Icons.Rounded.Draw,
        "➕" to Icons.Rounded.Add,
        "🤖" to Icons.Rounded.SmartToy,
    )

    /** The icon for [emoji], or null when it has none and should be drawn as the emoji itself. */
    fun forEmoji(emoji: String): ImageVector? = byEmoji[emoji.replace(VARIATION_SELECTOR, "")]

    // Emoji presentation selector: "✈️" is "✈" plus this.
    private const val VARIATION_SELECTOR = "️"
}
