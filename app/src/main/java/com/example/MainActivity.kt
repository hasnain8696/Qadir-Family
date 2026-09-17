package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import com.example.model.UserAccount
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.data.FamilyRepository
import com.example.ui.screens.*
import com.example.ui.theme.QadirFamilyTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            QadirFamilyApp()
        }
    }
}

@Composable
fun QadirFamilyApp() {
    val context = LocalContext.current
    val repository = remember { FamilyRepository.getInstance(context) }
    val navController = rememberNavController()

    val currentUser = repository.currentUser.collectAsState().value
    val startDest = remember {
        if (currentUser != null) {
            if (currentUser.role == "admin") {
                "admin_panel"
            } else if (!repository.canAccessFamilyTree()) {
                "verify_email?email=${android.net.Uri.encode(currentUser.email)}"
            } else {
                "main_tree"
            }
        } else {
            "welcome"
        }
    }

    QadirFamilyTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            NavHost(
                navController = navController,
                startDestination = startDest
            ) {
                // 1. Welcome / Landing
                composable("welcome") {
                    WelcomeScreen(
                        onNavigateToLogin = { navController.navigate("login") },
                        onNavigateToPasscode = { navController.navigate("passcode") }
                    )
                }

                // 2. Family Passcode Security Screen
                composable("passcode") {
                    PasscodeScreen(
                        repository = repository,
                        onPasscodeSuccess = {
                            navController.navigate("register") {
                                popUpTo("passcode") { inclusive = true }
                            }
                        },
                        onBack = { navController.popBackStack() }
                    )
                }

                // 3. Member Registration Screen
                composable("register") {
                    RegisterScreen(
                        repository = repository,
                        onRegisterSuccess = { registeredEmail ->
                            navController.navigate("verify_email?email=${android.net.Uri.encode(registeredEmail)}") {
                                popUpTo("welcome") { inclusive = false }
                            }
                        },
                        onBack = { navController.popBackStack() }
                    )
                }

                // 4. Member Login Screen
                composable("login") {
                    LoginScreen(
                        repository = repository,
                        onLoginSuccess = {
                            val user = repository.currentUser.value
                            if (user?.role == "admin") {
                                navController.navigate("admin_panel") {
                                    popUpTo("welcome") { inclusive = true }
                                }
                            } else if (user != null && !repository.canAccessFamilyTree()) {
                                navController.navigate("verify_email?email=${android.net.Uri.encode(user.email)}") {
                                    popUpTo("welcome") { inclusive = false }
                                }
                            } else {
                                navController.navigate("main_tree") {
                                    popUpTo("welcome") { inclusive = true }
                                }
                            }
                        },
                        onNavigateToForgotPassword = { navController.navigate("forgot_password") },
                        onBack = { navController.popBackStack() }
                    )
                }

                // 4b. Forgot Password Screen
                composable("forgot_password") {
                    ForgotPasswordScreen(
                        repository = repository,
                        onBack = { navController.popBackStack() }
                    )
                }

                // 4c. Verify Email Screen
                composable(
                    route = "verify_email?email={email}",
                    arguments = listOf(navArgument("email") {
                        type = NavType.StringType
                        defaultValue = ""
                        nullable = true
                    })
                ) { backStackEntry ->
                    val emailArg = backStackEntry.arguments?.getString("email") ?: ""
                    VerifyEmailScreen(
                        email = emailArg,
                        repository = repository,
                        onVerificationSuccess = {
                            val user = repository.currentUser.value
                            if (user?.role == "admin") {
                                navController.navigate("admin_panel") {
                                    popUpTo("welcome") { inclusive = true }
                                }
                            } else {
                                navController.navigate("main_tree") {
                                    popUpTo("welcome") { inclusive = true }
                                }
                            }
                        },
                        onLogout = {
                            repository.logout()
                            navController.navigate("welcome") {
                                popUpTo(0) { inclusive = true }
                            }
                        }
                    )
                }

                // 6. Natural Family Tree Screen (Main Hub)
                composable("main_tree") {
                    val user = repository.currentUser.collectAsState().value
                    LaunchedEffect(user) {
                        if (user != null && user.role != "admin" && !repository.canAccessFamilyTree()) {
                            navController.navigate("verify_email?email=${android.net.Uri.encode(user.email)}") {
                                popUpTo("main_tree") { inclusive = true }
                            }
                        }
                    }

                    MainTreeScreen(
                        repository = repository,
                        onNavigateToBranchZoom = { memberId ->
                            navController.navigate("branch_zoom/$memberId")
                        },
                        onNavigateToProfile = { memberId ->
                            navController.navigate("profile/$memberId")
                        },
                        onNavigateToDocuments = { memberId ->
                            val route = if (memberId != null) "documents?memberId=$memberId" else "documents"
                            navController.navigate(route)
                        },
                        onNavigateToAddMember = {
                            navController.navigate("add_member")
                        },
                        onNavigateToAddChild = { parentId ->
                            navController.navigate("add_member?parentId=$parentId")
                        },
                        onNavigateToAdmin = {
                            navController.navigate("admin_panel")
                        },
                        onLogout = {
                            repository.logout()
                            navController.navigate("welcome") {
                                popUpTo(0) { inclusive = true }
                            }
                        }
                    )
                }

                // 7. Branch Zoom View (Recursive navigation supported)
                composable(
                    route = "branch_zoom/{memberId}",
                    arguments = listOf(navArgument("memberId") { type = NavType.StringType })
                ) { backStackEntry ->
                    val memberId = backStackEntry.arguments?.getString("memberId") ?: ""
                    BranchZoomScreen(
                        memberId = memberId,
                        repository = repository,
                        onNavigateToBranchZoom = { deeperMemberId ->
                            navController.navigate("branch_zoom/$deeperMemberId")
                        },
                        onNavigateToProfile = { id -> navController.navigate("profile/$id") },
                        onNavigateToAddChild = { parentId -> navController.navigate("add_member?parentId=$parentId") },
                        onBackToFullTree = { navController.popBackStack() }
                    )
                }

                // 8. Member Profile Screen
                composable(
                    route = "profile/{memberId}",
                    arguments = listOf(navArgument("memberId") { type = NavType.StringType })
                ) { backStackEntry ->
                    val memberId = backStackEntry.arguments?.getString("memberId") ?: ""
                    MemberProfileScreen(
                        memberId = memberId,
                        repository = repository,
                        onNavigateToDocuments = { id -> navController.navigate("documents?memberId=$id") },
                        onNavigateToMember = { id -> navController.navigate("profile/$id") },
                        onBack = { navController.popBackStack() }
                    )
                }

                // 9. Documents Screen
                composable(
                    route = "documents?memberId={memberId}",
                    arguments = listOf(navArgument("memberId") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    })
                ) { backStackEntry ->
                    val memberId = backStackEntry.arguments?.getString("memberId")
                    DocumentsScreen(
                        initialMemberId = memberId,
                        repository = repository,
                        onBack = { navController.popBackStack() }
                    )
                }

                // 10. Add Family Member Screen
                composable(
                    route = "add_member?parentId={parentId}",
                    arguments = listOf(navArgument("parentId") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    })
                ) { backStackEntry ->
                    val parentId = backStackEntry.arguments?.getString("parentId")
                    AddMemberScreen(
                        initialParentId = parentId,
                        repository = repository,
                        onMemberAdded = { navController.popBackStack() },
                        onBack = { navController.popBackStack() }
                    )
                }

                // 11. Admin Panel Screen
                composable("admin_panel") {
                    val user = repository.currentUser.collectAsState().value
                    LaunchedEffect(user) {
                        if (user == null || user.role != "admin") {
                            navController.navigate("main_tree") {
                                popUpTo(0) { inclusive = true }
                            }
                        }
                    }

                    if (user?.role == "admin") {
                        AdminPanelScreen(
                            repository = repository,
                            onNavigateToAddMember = { navController.navigate("add_member") },
                            onNavigateToDocuments = { memberId ->
                                val route = if (memberId != null) "documents?memberId=$memberId" else "documents"
                                navController.navigate(route)
                            },
                            onLogout = {
                                repository.logout()
                                navController.navigate("welcome") {
                                    popUpTo(0) { inclusive = true }
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

// Provided for backward test compatibility
@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(text = "Hello $name!", modifier = modifier)
}
