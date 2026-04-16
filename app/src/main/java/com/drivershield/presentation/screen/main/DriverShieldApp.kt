package com.drivershield.presentation.screen.main

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.ui.res.stringResource
import com.drivershield.R
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.drivershield.presentation.screen.calendar.CalendarScreen
import com.drivershield.presentation.screen.export.ExportScreen
import com.drivershield.presentation.screen.history.HistoryScreen
import com.drivershield.presentation.screen.schedule.ScheduleScreen
import com.drivershield.presentation.theme.DriverShieldColors
import kotlinx.coroutines.launch

sealed class Screen(val route: String, @StringRes val titleRes: Int, val icon: ImageVector?) {
    data object Main : Screen("main", R.string.screen_dashboard, Icons.Default.Home)
    data object Calendar : Screen("calendar", R.string.calendar_title, Icons.Default.CalendarMonth)
    data object History : Screen("history", R.string.history_title, Icons.Default.History)
    data object Schedule : Screen("schedule", R.string.screen_schedule, Icons.Default.Schedule)
    data object Export : Screen("export", R.string.screen_export, Icons.Default.FileDownload)

    companion object {
        val allScreens = listOf(Main, Calendar, History, Schedule, Export)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DriverShieldApp() {
    val navController = rememberNavController()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(modifier = Modifier.width(280.dp)) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "DriverShield",
                    modifier = Modifier.padding(horizontal = 28.dp, vertical = 16.dp),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = DriverShieldColors.Accent
                )
                HorizontalDivider()
                Spacer(modifier = Modifier.height(8.dp))

                Screen.allScreens.forEach { item: Screen ->
                    NavigationDrawerItem(
                        label = { Text(text = stringResource(item.titleRes)) },
                        selected = (navController.currentDestination?.route == item.route),
                        onClick = {
                            scope.launch { drawerState.close() }
                            if (item.route == Screen.Main.route) {
                                navController.navigate(item.route) {
                                    popUpTo(Screen.Main.route) { inclusive = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            } else {
                                navController.navigate(item.route)
                            }
                        },
                        icon = {
                            item.icon?.let { iconVector: ImageVector ->
                                Icon(imageVector = iconVector, contentDescription = stringResource(item.titleRes))
                            }
                        },
                        colors = NavigationDrawerItemDefaults.colors(
                            selectedContainerColor = DriverShieldColors.Accent.copy(alpha = 0.1f),
                            selectedTextColor = DriverShieldColors.Accent,
                            selectedIconColor = DriverShieldColors.Accent
                        ),
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                }
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        val currentRoute = navController.currentDestination?.route
                        val titleRes = Screen.allScreens.find { it.route == currentRoute }?.titleRes ?: R.string.app_name
                        Text(text = stringResource(titleRes), fontWeight = FontWeight.Bold)
                    },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(imageVector = Icons.Default.Menu, contentDescription = stringResource(R.string.cd_open_menu))
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = DriverShieldColors.AmoledBlack,
                        titleContentColor = DriverShieldColors.OnSurface,
                        navigationIconContentColor = DriverShieldColors.OnSurface
                    )
                )
            },
            containerColor = DriverShieldColors.AmoledBlack
        ) { paddingValues ->
            NavHost(
                navController = navController,
                startDestination = Screen.Main.route,
                modifier = Modifier.padding(paddingValues)
            ) {
                composable(Screen.Main.route) { MainScreen() }
                composable(Screen.Calendar.route) { CalendarScreen() }
                composable(Screen.History.route) { HistoryScreen() }
                composable(Screen.Schedule.route) { ScheduleScreen() }
                composable(Screen.Export.route) { ExportScreen() }
            }
        }
    }
}