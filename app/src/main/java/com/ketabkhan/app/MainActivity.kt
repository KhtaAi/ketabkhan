package com.ketabkhan.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.ketabkhan.app.data.pdf.BookRepository
import com.ketabkhan.app.domain.tts.UnavailableTtsEngine
import com.ketabkhan.app.ui.screens.BookScreen
import com.ketabkhan.app.ui.screens.BookViewModel
import com.ketabkhan.app.ui.screens.LibraryScreen
import com.ketabkhan.app.ui.screens.LibraryViewModel
import com.ketabkhan.app.ui.theme.KetabkhanTheme

private const val ROUTE_LIBRARY = "library"
private const val ROUTE_BOOK = "book/{bookId}"

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      KetabkhanTheme {
        KetabkhanApp()
      }
    }
  }
}

@Composable
fun KetabkhanApp() {
  val context = LocalContext.current.applicationContext
  val repository = remember(context) { BookRepository(context) }
  val ttsEngine = remember { UnavailableTtsEngine() }
  val navController = rememberNavController()

  NavHost(
    navController = navController,
    startDestination = ROUTE_LIBRARY,
  ) {
    composable(ROUTE_LIBRARY) {
      val libraryViewModel: LibraryViewModel = viewModel(
        factory = LibraryViewModel.Factory(repository),
      )
      LibraryScreen(
        viewModel = libraryViewModel,
        onOpenBook = { bookId ->
          navController.navigate("book/$bookId") {
            launchSingleTop = true
          }
        },
      )
    }

    composable(
      route = ROUTE_BOOK,
      arguments = listOf(
        navArgument("bookId") { type = NavType.StringType },
      ),
    ) { backStackEntry ->
      val bookId = backStackEntry.arguments?.getString("bookId").orEmpty()
      val bookViewModel: BookViewModel = viewModel(
        key = "book_$bookId",
        factory = BookViewModel.Factory(
          bookId = bookId,
          repository = repository,
          ttsEngine = ttsEngine,
        ),
      )
      BookScreen(
        viewModel = bookViewModel,
        onNavigateBack = { navController.popBackStack() },
      )
    }
  }
}
