package me.dougals.forage.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import me.dougals.forage.ui.articles.ArticleListScreen
import me.dougals.forage.ui.categories.CategoriesScreen
import me.dougals.forage.ui.login.LoginScreen
import me.dougals.forage.ui.reader.ReaderScreen
import me.dougals.forage.ui.settings.SettingsScreen

private enum class Tab(val route: String, val label: String, val icon: ImageVector, val source: String? = null) {
    Unread("list/unread", "Unread", Icons.Outlined.Inbox, "unread"),
    Categories("categories", "Categories", Icons.Outlined.Folder),
    Saved("list/saved", "Saved", Icons.Outlined.Bookmarks, "saved"),
    Settings("settings", "Settings", Icons.Outlined.Settings),
}

// One destination per list: tabs that share a destination would share saved back-stack state.
private const val UNREAD_ROUTE = "list/unread"
private const val SAVED_ROUTE = "list/saved"
private const val FEED_ROUTE = "list/feed?feedId={feedId}"
private fun isListRoute(route: String?) = route != null && route.startsWith("list/")
private const val READER_ROUTE = "reader/{articleId}"
private const val LOGIN_ROUTE = "login?url={url}"

/** The screens behind each destination, so navigation can be tested with stand-ins. */
class ForageScreens(
    val list: @Composable (kind: String, nav: NavHostController) -> Unit,
    val categories: @Composable (nav: NavHostController) -> Unit,
    val settings: @Composable (nav: NavHostController) -> Unit,
    val reader: @Composable (nav: NavHostController) -> Unit,
    val login: @Composable (startUrl: String, nav: NavHostController) -> Unit,
)

private val RealScreens = ForageScreens(
    list = { kind, nav ->
        ArticleListScreen(
            onOpenArticle = { nav.navigate("reader/$it") },
            onBack = if (kind == "feed") ({ nav.popBackStack() }) else null,
        )
    },
    categories = { nav -> CategoriesScreen(onOpenFeed = { nav.navigate("list/feed?feedId=$it") }) },
    settings = { nav -> SettingsScreen(onLogin = { url -> nav.navigate("login?url=${android.net.Uri.encode(url)}") }) },
    reader = { nav -> ReaderScreen(onBack = { nav.popBackStack() }) },
    login = { url, nav -> LoginScreen(startUrl = url, onDone = { nav.popBackStack() }) },
)

@Composable
fun ForageRoot() = ForageNavScaffold(RealScreens)

@Composable
fun ForageNavScaffold(screens: ForageScreens) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val destination = entry?.destination
    val source = entry?.arguments?.getString("source")
    val onTopLevel = destination?.route == "categories" || destination?.route == "settings" ||
        (isListRoute(destination?.route) && source in listOf("unread", "saved"))

    Scaffold(
        bottomBar = {
            if (onTopLevel) {
                NavigationBar {
                    Tab.entries.forEach { tab ->
                        val selected = if (tab.source != null) {
                            isListRoute(destination?.route) && source == tab.source
                        } else {
                            destination?.hierarchy?.any { it.route == tab.route } == true
                        }
                        NavigationBarItem(
                            selected = selected,
                            onClick = { nav.navigateTab(tab.route) },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        },
    ) { inner ->
        NavHost(nav, startDestination = Tab.Unread.route, modifier = Modifier
                // Screens own their top bar and status-bar inset; only reserve the bottom nav here.
                .padding(bottom = inner.calculateBottomPadding())
                .consumeWindowInsets(PaddingValues(bottom = inner.calculateBottomPadding())),
        ) {
            listOf(
                UNREAD_ROUTE to "unread",
                SAVED_ROUTE to "saved",
                FEED_ROUTE to "feed",
            ).forEach { (route, kind) ->
                composable(
                    route,
                    arguments = listOf(
                        navArgument("source") { type = NavType.StringType; defaultValue = kind },
                        navArgument("feedId") { type = NavType.LongType; defaultValue = -1L },
                    ),
                ) { screens.list(kind, nav) }
            }
            composable("categories") { screens.categories(nav) }
            composable("settings") { screens.settings(nav) }
            composable(READER_ROUTE, arguments = listOf(navArgument("articleId") { type = NavType.LongType })) {
                screens.reader(nav)
            }
            composable(
                LOGIN_ROUTE,
                arguments = listOf(navArgument("url") { type = NavType.StringType; defaultValue = "" }),
            ) { e -> screens.login(e.arguments?.getString("url").orEmpty(), nav) }
        }
    }
}

private fun NavHostController.navigateTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
