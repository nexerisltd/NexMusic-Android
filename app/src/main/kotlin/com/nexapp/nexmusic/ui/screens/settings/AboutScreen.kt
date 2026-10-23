@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.nexapp.nexmusic.ui.screens.settings

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.nexapp.nexmusic.BuildConfig
import com.nexapp.nexmusic.LocalPlayerAwareWindowInsets
import com.nexapp.nexmusic.R
import com.nexapp.nexmusic.ui.component.IconButton
import com.nexapp.nexmusic.ui.component.Material3SettingsGroup
import com.nexapp.nexmusic.ui.component.Material3SettingsItem
import com.nexapp.nexmusic.ui.utils.backToMain
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray

private const val DEV_PROFILE_URL = "https://nexeristm.vercel.app/api/public/profiles/mrarxme"

private data class DevProfile(
  val avatarUrl: String?,
  val fullName: String?,
  val position: String?,
)

private fun fetchDevProfile(): DevProfile? =
  try {
    val conn =
      (java.net.URL(DEV_PROFILE_URL).openConnection() as java.net.HttpURLConnection).apply {
        connectTimeout = 15_000
        readTimeout = 15_000
        requestMethod = "GET"
        setRequestProperty("Accept", "application/json")
      }
    try {
      if (conn.responseCode !in 200..299) {
        null
      } else {
        val body = conn.inputStream.bufferedReader().use { it.readText() }
        val profile = org.json.JSONObject(body).getJSONObject("profile")
        fun field(key: String): String? =
          profile.optString(key, "").takeIf { it.isNotBlank() && it != "null" }
        DevProfile(field("avatar_url"), field("full_name"), field("position"))
      }
    } finally {
      conn.disconnect()
    }
  } catch (e: Exception) {
    e.printStackTrace()
    null
  }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
  navController: NavController,
  scrollBehavior: TopAppBarScrollBehavior,
  onBack: (() -> Unit)? = null,
  highlightKey: String? = null
) {
  val uriHandler = LocalUriHandler.current

  data class Contributor(val login: String, val avatarUrl: String, val htmlUrl: String)
  var contributors by remember { mutableStateOf<List<Contributor>>(emptyList()) }
  var devProfile by remember { mutableStateOf<DevProfile?>(null) }

  LaunchedEffect(Unit) { devProfile = withContext(Dispatchers.IO) { fetchDevProfile() } }

  LaunchedEffect(Unit) {
    withContext(Dispatchers.IO) {
      try {
        val url = java.net.URL("https://api.github.com/repos/nexerisltd/NexMusic-Android/contributors")
        val json = url.openStream().bufferedReader().use { it.readText() }
        val array = JSONArray(json)
        val list = mutableListOf<Contributor>()
        for (i in 0 until array.length()) {
          val obj = array.getJSONObject(i)
          list.add(
            Contributor(
              obj.getString("login"),
              obj.getString("avatar_url"),
              obj.getString("html_url")
            )
          )
        }
        contributors = list
      } catch (e: Exception) {
        e.printStackTrace()
      }
    }
  }
  val context = LocalContext.current

  Scaffold(
    modifier = Modifier.fillMaxSize(),
    containerColor = MaterialTheme.colorScheme.surface,
    contentWindowInsets = WindowInsets(0, 0, 0, 0),
    topBar = {
      TopAppBar(
        title = {
          Text(
            text = stringResource(R.string.about),
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        },
        navigationIcon = {
          IconButton(
            onClick = { onBack?.invoke() ?: navController.navigateUp() },
            onLongClick = navController::backToMain,
          ) {
            Icon(painterResource(R.drawable.arrow_back), contentDescription = null)
          }
        },
        windowInsets = TopAppBarDefaults.windowInsets,
        colors =
          TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
          ),
        scrollBehavior = scrollBehavior,
      )
    },
  ) { innerPadding ->
    LazyColumn(
      modifier =
        Modifier.fillMaxSize()
          .windowInsetsPadding(
            LocalPlayerAwareWindowInsets.current.only(
              WindowInsetsSides.Horizontal,
            ),
          ),
      contentPadding =
        PaddingValues(
          start = 16.dp,
          top = innerPadding.calculateTopPadding() + 8.dp,
          end = 16.dp,
          bottom =
            androidx.compose.foundation.layout.WindowInsets.systemBars
              .asPaddingValues()
              .calculateBottomPadding() + 32.dp,
        ),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      item { AboutAppCard() }

      if (contributors.isNotEmpty()) {
        item {
          Column(modifier = Modifier.fillMaxWidth()) {
            com.nexapp.nexmusic.ui.component.PreferenceGroupTitle(title = "Contributors")
            Row(
              modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
              horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
              contributors.forEach { contributor ->
                coil3.compose.AsyncImage(
                  model = contributor.avatarUrl,
                  contentDescription = contributor.login,
                  modifier =
                    Modifier.size(48.dp).clip(CircleShape).clickable {
                      uriHandler.openUri(contributor.htmlUrl)
                    },
                  contentScale = androidx.compose.ui.layout.ContentScale.Crop
                )
              }
            }
          }
        }
      }

      item { DeveloperCard(devProfile) }

      item {
        Material3SettingsGroup(
          title = "Developer",
          items =
            listOf(
              Material3SettingsItem(
                icon = painterResource(R.drawable.github),
                title = { Text("GitHub") },
                description = { Text("@mrarxme") },
                onClick = { uriHandler.openUri("https://github.com/mrarxme") }
              ),
              Material3SettingsItem(
                icon = painterResource(R.drawable.ic_instagram_new),
                title = { Text("Instagram") },
                description = { Text("@mr.arx.me") },
                onClick = { uriHandler.openUri("https://instagram.com/mr.arx.me") }
              ),
              Material3SettingsItem(
                icon = painterResource(R.drawable.ic_telegram_new),
                title = { Text("Telegram") },
                description = { Text("@mrarxme") },
                onClick = { uriHandler.openUri("https://t.me/mrarxme") }
              )
            )
        )
      }


      item {
        Material3SettingsGroup(
          title = "Community",
          items =
            listOf(
              Material3SettingsItem(
                icon = painterResource(R.drawable.ic_telegram_new),
                title = { Text("Telegram") },
                description = { Text("t.me/nexappog") },
                onClick = { uriHandler.openUri("https://t.me/nexappog") }
              ),
              Material3SettingsItem(
                icon = painterResource(R.drawable.ic_discord_new),
                title = { Text("Discord") },
                description = { Text("discord.gg/P44QdHPtKg") },
                onClick = { uriHandler.openUri("https://discord.gg/P44QdHPtKg") }
              )
            )
        )
      }

      /* item {
          AboutSectionCard(title = "App") {
              AboutActionRow(
                  icon = painterResource(R.drawable.github),
                  title = "GitHub",
                  subtitle = "nexerisltd/NexMusic-Android",
                  onClick = { uriHandler.openUri("https://github.com/nexerisltd/NexMusic-Android") },
              )
              AboutDivider()
              AboutActionRow(
                  icon = painterResource(R.drawable.ic_discord_new),
                  title = "Discord",
                  subtitle = "discord.gg/P44QdHPtKg",
                  onClick = { uriHandler.openUri("https://discord.gg/P44QdHPtKg") },
              )
              AboutDivider()
              AboutActionRow(
                  icon = painterResource(R.drawable.ic_telegram_new),
                  title = "Telegram",
                  subtitle = "t.me/nexappog",
                  onClick = { uriHandler.openUri("https://t.me/nexappog") },
              )
          }
      } */

    }
  }
}

@Composable
private fun AboutAppCard() {
  Column(
    modifier = Modifier.fillMaxWidth().padding(vertical = 28.dp, horizontal = 20.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f

    Image(
      painter = painterResource(R.drawable.ic_launcher_nobg),
      contentDescription = null,
      colorFilter = ColorFilter.tint(if (isDark) Color.White else Color(0xFFEA3829)),
      modifier = Modifier.size(100.dp)
    )

    Spacer(Modifier.height(4.dp))

    Text(
      text = "NexMusic",
      style = MaterialTheme.typography.titleLarge,
      fontWeight = FontWeight.Bold,
      color = MaterialTheme.colorScheme.onSurface,
    )
    Row(
      horizontalArrangement = Arrangement.spacedBy(6.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
      ) {
        Text(
          text = BuildConfig.VERSION_NAME,
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.primary,
          fontWeight = FontWeight.Medium,
          modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
      }
      if (BuildConfig.DEBUG) {
        Surface(
          shape = RoundedCornerShape(8.dp),
          color = MaterialTheme.colorScheme.error.copy(alpha = 0.10f),
        ) {
          Text(
            text = "DEBUG",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.error,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
          )
        }
      } else {
        Surface(
          shape = RoundedCornerShape(8.dp),
          color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.10f),
        ) {
          Text(
            text = BuildConfig.ARCHITECTURE.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.secondary,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
          )
        }
      }
    }
  }
}

private const val DEV_FALLBACK_AVATAR =
  "https://kkrglsaoivkbadlxllbp.supabase.co/storage/v1/object/public/avatars/cc2db264-407f-4d22-be8a-dfd4146497f7/1791632625170.png"

@Composable
private fun DeveloperCard(devProfile: DevProfile?) {
  Surface(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(24.dp),
    color = MaterialTheme.colorScheme.surfaceContainerHigh,
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(16.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      coil3.compose.AsyncImage(
        model = devProfile?.avatarUrl ?: DEV_FALLBACK_AVATAR,
        contentDescription = null,
        modifier = Modifier.size(64.dp).clip(CircleShape),
        contentScale = androidx.compose.ui.layout.ContentScale.Crop
      )
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = devProfile?.fullName ?: "MR. ARX",
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.onSurface,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Text(
          text = devProfile?.position ?: "Senior Developer",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
        )
      }
    }
  }
}
