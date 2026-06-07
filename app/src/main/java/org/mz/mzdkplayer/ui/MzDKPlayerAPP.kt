package org.mz.mzdkplayer.ui

import org.mz.mzdkplayer.ui.screen.setting.SolarSystem
import android.net.Uri
import android.util.Log
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.util.UnstableApi
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navOptions
import androidx.tv.material3.DrawerState
import androidx.tv.material3.DrawerValue
import androidx.tv.material3.Icon
import androidx.tv.material3.ListItem
import androidx.tv.material3.ListItemDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.ModalNavigationDrawer
import androidx.tv.material3.NavigationDrawer
import androidx.tv.material3.NavigationDrawerItem
import androidx.tv.material3.NavigationDrawerItemDefaults
import androidx.tv.material3.Text
import org.mz.mzdkplayer.MzDkPlayerApplication
import org.mz.mzdkplayer.R
import org.mz.mzdkplayer.data.model.FTPConnection
import org.mz.mzdkplayer.data.model.NFSConnection
import org.mz.mzdkplayer.data.model.WebDavConnection
import org.mz.mzdkplayer.di.RepositoryProvider
import org.mz.mzdkplayer.tool.Tools.toSafeInt
import org.mz.mzdkplayer.tool.viewModelWithFactory
import org.mz.mzdkplayer.ui.audioplayer.AudioPlayerScreen

import org.mz.mzdkplayer.ui.picviewer.PicViewerScreen
import org.mz.mzdkplayer.ui.screen.common.EditTMDBInfoScreen
import org.mz.mzdkplayer.ui.screen.movie.MovieDetailsScreen
import org.mz.mzdkplayer.ui.screen.history.MediaHistoryScreen
import org.mz.mzdkplayer.ui.screen.ftp.FTPConListScreen
import org.mz.mzdkplayer.ui.screen.ftp.FTPConScreen
import org.mz.mzdkplayer.ui.screen.ftp.FTPFileListScreen
import org.mz.mzdkplayer.ui.screen.home.FileHomeScreen
import org.mz.mzdkplayer.ui.screen.httplink.HTTPLinkFileListScreen
import org.mz.mzdkplayer.ui.screen.httplink.HTTPLinkConListScreen

import org.mz.mzdkplayer.ui.screen.localfile.LocalFileListScreen
import org.mz.mzdkplayer.ui.screen.localfile.LocalFileTypeScreen
import org.mz.mzdkplayer.ui.screen.httplink.HTTPLinkConScreen
import org.mz.mzdkplayer.ui.screen.library.AudioLibraryScreen
import org.mz.mzdkplayer.ui.screen.library.MovieLibraryScreen
import org.mz.mzdkplayer.ui.screen.library.TvLibraryScreen
import org.mz.mzdkplayer.ui.screen.nfs.NFSConListScreen
import org.mz.mzdkplayer.ui.screen.nfs.NFSConScreen
import org.mz.mzdkplayer.ui.screen.nfs.NFSFileListScreen
import org.mz.mzdkplayer.ui.screen.search.SearchScreen
import org.mz.mzdkplayer.ui.screen.setting.BlackHoleSimulationScreen
import org.mz.mzdkplayer.ui.screen.smbfile.SMBConListScreen
import org.mz.mzdkplayer.ui.screen.smbfile.SMBConScreen
import org.mz.mzdkplayer.ui.screen.smbfile.SMBFileListScreen
import org.mz.mzdkplayer.ui.screen.webdavfile.WebDavConListScreen

import org.mz.mzdkplayer.ui.screen.webdavfile.WebDavConScreen
import org.mz.mzdkplayer.ui.screen.webdavfile.WebDavFileListScreen
import org.mz.mzdkplayer.ui.screen.setting.SettingsScreen
import org.mz.mzdkplayer.ui.screen.tv.TVSeriesDetailsScreen
import org.mz.mzdkplayer.ui.screen.vm.AudioViewModel
import org.mz.mzdkplayer.ui.screen.vm.FTPListViewModel
import org.mz.mzdkplayer.ui.screen.vm.HTTPLinkListViewModel

import org.mz.mzdkplayer.ui.screen.vm.MediaHistoryViewModel
import org.mz.mzdkplayer.ui.screen.vm.MediaLibraryViewModel
import org.mz.mzdkplayer.ui.screen.vm.NFSListViewModel
import org.mz.mzdkplayer.ui.screen.vm.SMBListViewModel
import org.mz.mzdkplayer.ui.screen.vm.SettingsViewModel
import org.mz.mzdkplayer.ui.screen.vm.WebDavListViewModel
import org.mz.mzdkplayer.ui.theme.mySideListItemColor

import org.mz.mzdkplayer.ui.videoplayer.VideoPlayerScreen
import java.net.URLDecoder
import java.net.URLEncoder

@OptIn(UnstableApi::class)
@Composable
fun MzDKPlayerAPP(externalVideoUri: Uri?) {


    var selectedIndex by remember { mutableIntStateOf(0) }
    val items =
        listOf(

            "电影" to painterResource(id = R.drawable.moviefileicon),
            "电视" to painterResource(id = R.drawable.tv24dp),
            "音乐" to painterResource(id = R.drawable.librarymusic24dp),
            "继续播放" to painterResource(id = R.drawable.history24dp),
            "文件浏览" to painterResource(id = R.drawable.baseline_folder_24),
            "搜索" to painterResource(id = R.drawable.baseline_search_24),
            "设置" to painterResource(id = R.drawable.baseline_settings_24),
        )
    var backPressedTime by remember { mutableLongStateOf(0L) }
    val backPressThreshold = 2000L // 2秒内再次按返回键才退出
    val mainNavController = rememberNavController()
    val len = items.size
    val context = LocalContext.current
    val currentRoute = mainNavController.currentBackStackEntryAsState().value?.destination?.route
    // 判断是否为主页面（需要显示侧边栏）
    val isMainPage = currentRoute in listOf("HomePage", "HistoryPage", "SettingsPage")

    // 👇 关键：如果 externalVideoUri 存在，直接播放，不显示主页
    LaunchedEffect(externalVideoUri) {
        if (externalVideoUri != null) {
            val uriString = externalVideoUri.toString()
            Log.i("externalVideoUri",uriString)
            mainNavController.navigate(
                "VideoPlayer/${URLEncoder.encode(uriString, "UTF-8")}/HTTP/外部视频/外部视频"
            )
        }
    }

    val smbListViewModel: SMBListViewModel = viewModel()
    val webDavListViewModel: WebDavListViewModel = viewModel()
    val ftpListViewModel: FTPListViewModel = viewModel()
    val httpLinkListViewModel: HTTPLinkListViewModel = viewModel()
    val nfsListViewModel: NFSListViewModel = viewModel()
    //val webDavListViewModel: WebDavListViewModel = viewModel()

    // 使用工厂初始化 ViewModel
    val libraryViewModel: MediaLibraryViewModel = viewModelWithFactory {
        RepositoryProvider.createMediaLibraryViewModel()
    }
    val audioViewModel: AudioViewModel = viewModelWithFactory {
        RepositoryProvider.createAudioViewModel()
    }

    val mediaHistoryViewModel: MediaHistoryViewModel = viewModelWithFactory {RepositoryProvider.createMediaHistoryViewModel()  }
    val settingsVM: SettingsViewModel = viewModel()
    val settingsState by settingsVM.uiState.collectAsState()
    NavHost(
        navController = mainNavController,
        startDestination = "MainPage",
        modifier = Modifier.background(Color.Black)
    )
    {

        composable("MainPage") {
            val homeNavController = rememberNavController()
            val sideFocusRequest = remember { FocusRequester() }
            LaunchedEffect(Unit) {
                sideFocusRequest.requestFocus()
            }
            val drawerState = remember { DrawerState(initialValue = DrawerValue.Closed) }
            ModalNavigationDrawer(
                drawerState = drawerState, // 直接传入保存的状态,
                modifier = Modifier.fillMaxSize(),
                drawerContent = {drawerValue ->
                    val isOpen = drawerValue == DrawerValue.Open
                    val warmWhite = Color(0xFFFFF6E5)       // 定义暖白色
                    Column(
                        Modifier
                            // 展开时使用深色半透明背景，收起时完全透明
                            // 👇 修改 1：使用水平渐变色
                            .background(
                                if (isOpen) {
                                    Brush.horizontalGradient(
                                        colors = listOf(
                                            Color(0xE6000000), // 左侧：90% 黑
                                            Color(0x00000000)  // 右侧：完全透明
                                        )
                                    )
                                } else {
                                    SolidColor(Color.Transparent) // 收起时纯透明
                                }
                            )

                            .fillMaxHeight()
                            .padding(vertical = 24.dp, horizontal = 12.dp)
                            // 动画过渡宽度
                            .width(if (isOpen) 240.dp else 64.dp)
                            .selectableGroup(),
                        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
                    ) {
                        items.forEachIndexed { index, item ->
                            val (text, icon) = item
                            val isSelected = selectedIndex == index // 提取一个变量方便复用
                            NavigationDrawerItem(

                                selected = isSelected,
//
                                onClick = {
                                    selectedIndex = index
                                    when (selectedIndex) {
                                        0 ->  homeNavController.navigate(
                                            "MoviesPage",
                                            navOptions = navOptions {
                                                launchSingleTop = true
                                                popUpTo("MoviesPage") {
                                                    inclusive = true
                                                }
                                            })
                                        1 ->homeNavController.navigate(
                                            "TvLibraryPage",
                                            navOptions = navOptions {
                                                launchSingleTop = true
                                                popUpTo("TvLibraryPage") {
                                                    inclusive = true
                                                }
                                            })
                                        2 ->homeNavController.navigate(
                                            "AudioLibraryPage",
                                            navOptions = navOptions {
                                                launchSingleTop = true
                                                popUpTo("AudioLibraryPage") {
                                                    inclusive = true
                                                }
                                            })
                                        3 -> homeNavController.navigate(
                                            "HistoryPage",
                                            navOptions = navOptions {
                                                launchSingleTop = true
                                                popUpTo("HistoryPage") {
                                                    inclusive = true
                                                }
                                            })
                                        4 -> homeNavController.navigate(
                                            "FileHomePage",
                                            navOptions = navOptions {
                                                launchSingleTop = true
                                                popUpTo("FileHomePage") {
                                                    inclusive = true
                                                }
                                            })
                                        5 ->homeNavController.navigate(
                                            "SearchPage",
                                            navOptions = navOptions {
                                                launchSingleTop = true
                                                popUpTo("SearchPage") {
                                                    inclusive = true
                                                }
                                            })

                                        6 -> homeNavController.navigate(
                                            "SettingsPage",
                                            navOptions = navOptions {
                                                launchSingleTop = true
                                                popUpTo("SettingsPage") {
                                                    inclusive = true
                                                }
                                            })
                                    }
                                },
                                leadingContent = {
                                    Icon(
                                        painter = icon,
                                        contentDescription = text,
                                        // 👇 修改 2：强制统一所有图标大小为 24.dp
                                        modifier = Modifier.size(24.dp),
                                        // 👇 修改 2：选中时图标变黑，未选中时保持半透明白
                                        tint = if (isSelected) Color.Black else Color.White.copy(alpha = 0.6f)
                                    )
                                },
                                // 👇 修改 3：配置 Item 的底色
                                colors = NavigationDrawerItemDefaults.colors(
                                    containerColor = Color.Transparent,                     // 默认底色透明
                                    focusedContainerColor = Color.White.copy(alpha = 0.15f),// 未选中但焦点悬停时的底色
                                    selectedContainerColor = warmWhite,                     // 选中时的底色 (暖白)
                                    focusedSelectedContainerColor = warmWhite               // 选中且焦点悬停时的底色 (暖白)
                                ),
                                // ✅ 关键修改：把焦点请求器绑定到第 1 个 Item 上
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .then(
                                        if (index == 0) Modifier.focusRequester(sideFocusRequest)
                                        else Modifier
                                    )

                            ){
                                if (isOpen) {
                                    Text(
                                        text = text,
                                        // 👇 修改 4：选中时文字变黑，未选中时纯白
                                        color = if (isSelected) Color.Black else Color.White,
                                        modifier = Modifier.padding(start = 12.dp),
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                }
                            }
                        }
                    }

                },

                content = {
                    NavHost(
                        navController = homeNavController,
                        startDestination = "MoviesPage",
                        modifier = Modifier
                            .background(Color.Black)
                            .fillMaxHeight()
                            .fillMaxWidth()
                            // 👇 关键修改：统一在这里加上 64.dp 的左侧边距
                            .padding(start = 64.dp)
                    ) {
                        //声明名为MainPage的页面路由
                        composable("FileHomePage") {
                            //页面路由对应的页面组件
                            FileHomeScreen(mainNavController)
                        }
                        composable("SettingsPage") {
                            //页面路由对应的页面组件
                            SettingsScreen(mainNavController,settingsVM,audioViewModel)
                        }
                        composable("HistoryPage") {
                            //页面路由对应的页面组件
                            MediaHistoryScreen(mainNavController,mediaHistoryViewModel)
                        }
                        composable("MoviesPage") {
                            //页面路由对应的页面组件
                            MovieLibraryScreen(libraryViewModel,mainNavController,homeNavController,settingsVM)
                        }

                        composable("TvLibraryPage") {
                            //页面路由对应的页面组件
                            TvLibraryScreen(libraryViewModel,mainNavController,homeNavController,settingsVM)
                        }
                        composable("AudioLibraryPage") {
                            //页面路由对应的页面组件
                            AudioLibraryScreen(audioViewModel,homeNavController,mainNavController)
                        }
                        composable("SearchPage") {
                            //页面路由对应的页面组件
                            SearchScreen(mainNavController)
                        }

                    }
                })
        }
        composable("LocalFileTypeScreen") { backStackEntry ->
            LocalFileTypeScreen(mainNavController)
        }
        composable("LocalFileListScreen/{path}") { backStackEntry ->
            val encodedPath = backStackEntry.arguments?.getString("path")
            if (encodedPath != null) {
                val path = URLDecoder.decode(encodedPath, "UTF-8")
                Log.d("encodedPath", path)
                LocalFileListScreen(path, mainNavController,settingsVM)

            }
        }
        composable("VideoPlayer/{sourceUri}/{dataSourceType}/{fileName}/{connectionName}") { backStackEntry ->

            //页面路由对应的页面组件
            val sourceUri = backStackEntry.arguments?.getString("sourceUri")
            val dataSourceType = backStackEntry.arguments?.getString("dataSourceType")
            val fileName = backStackEntry.arguments?.getString("fileName")
            val connectionName = backStackEntry.arguments?.getString("connectionName") ?: "未知"

            // ============ 强制 VLC 判断逻辑 ============

            // 检查参数是否不为空，并渲染屏幕
            if (sourceUri != null && dataSourceType != null && fileName != null) {
                //Log.d("sourceUri", sourceUri)
                //Log.d("dataSourceType", dataSourceType)
                val decodedUri = URLDecoder.decode(sourceUri, "UTF-8")
                val extension = decodedUri.substringAfterLast('.').lowercase()
                val forceVlcByExtension = extension in listOf("m2ts", "iso", "m2t", "mts","ts")
                Log.d("VideoPlayer", "✅ Decoded MRL（传给 VLC）: $decodedUri")   // ← 关键！
                val shouldUseVlc = forceVlcByExtension || (settingsState.defaultPlayer == "vlc")
                VideoPlayerScreen(
                    decodedUri,
                    dataSourceType,
                    URLDecoder.decode(fileName, "UTF-8"),
                    URLDecoder.decode(connectionName, "UTF-8"),
                    mediaHistoryViewModel,
                    shouldUseVlc,
                    settingsVM
                )
            }
        }
        composable("AudioPlayer/{sourceUri}/{dataSourceType}/{fileName}/{connectionName}/{currentIndex}") { backStackEntry ->

            //页面路由对应的页面组件
            val sourceUri = backStackEntry.arguments?.getString("sourceUri")
            val dataSourceType = backStackEntry.arguments?.getString("dataSourceType")
            val fileName = backStackEntry.arguments?.getString("fileName")
            val currentIndex = backStackEntry.arguments?.getString("currentIndex") ?: "1"
            val connectionName = backStackEntry.arguments?.getString("connectionName") ?: "未知"
            // 获取特定的字符串列表
            val extraList = MzDkPlayerApplication.getStringList("audio_playlist")
            // 检查参数是否不为空，并渲染屏幕
            if (sourceUri != null && dataSourceType != null) {
                Log.d("sourceUri", sourceUri)
                Log.d("dataSourceType", dataSourceType)
                AudioPlayerScreen(
                    URLDecoder.decode(sourceUri, "UTF-8"),
                    dataSourceType,
                    URLDecoder.decode(fileName, "UTF-8") ?: "未知文件名",
                    extraList,
                    currentIndex = currentIndex,
                    URLDecoder.decode(connectionName, "UTF-8"),
                    mediaHistoryViewModel,
                    audioViewModel = audioViewModel

                )
            }
        }
        composable("PicViewer/{sourceUri}/{dataSourceType}/{fileName}/{connectionName}") { backStackEntry ->

            //页面路由对应的页面组件
            val sourceUri = backStackEntry.arguments?.getString("sourceUri")
            val dataSourceType = backStackEntry.arguments?.getString("dataSourceType")
            val fileName = backStackEntry.arguments?.getString("fileName")

            val connectionName = backStackEntry.arguments?.getString("connectionName") ?: "未知"

            // 检查参数是否不为空，并渲染屏幕
            if (sourceUri != null && dataSourceType != null) {
                Log.d("sourceUri", sourceUri)
                Log.d("dataSourceType", dataSourceType)
                PicViewerScreen(
                    URLDecoder.decode(sourceUri, "UTF-8"),
                    dataSourceType,
                    URLDecoder.decode(fileName, "UTF-8") ?: "未知文件名",
                    URLDecoder.decode(connectionName, "UTF-8"),
                )
            }
        }
        composable("EditTMDBInfoScreen/{mediaUri}") { backStackEntry ->
            val mediaUri = backStackEntry.arguments?.getString("mediaUri")
            EditTMDBInfoScreen(URLDecoder.decode(mediaUri, "UTF-8"), navController = mainNavController)
        }

        composable("SMBFileListScreen/{path}/{connectionName}") { backStackEntry ->
            val encodedPath = backStackEntry.arguments?.getString("path")
            val connectionName = backStackEntry.arguments?.getString("connectionName") ?: "未知"
            if (encodedPath != null) {

                val path = URLDecoder.decode(encodedPath, "UTF-8")
                Log.d("encodedPath", path)
                SMBFileListScreen(path, mainNavController, connectionName,settingsVM)
            }
        }
        composable("WebDavFileListScreen/{path}/{username}/{pw}/{connectionName}") { backStackEntry ->
            val encodedPath = backStackEntry.arguments?.getString("path")
            val username = backStackEntry.arguments?.getString("username")
            val pw = backStackEntry.arguments?.getString("pw")
            val connectionName = backStackEntry.arguments?.getString("connectionName") ?: "未知"
            if (encodedPath != null && username != null && pw != null) {

                val path = URLDecoder.decode(encodedPath, "UTF-8")
                Log.d("encodedPath", path)
                WebDavFileListScreen(
                    path,
                    mainNavController,
                    WebDavConnection("1", connectionName, path, username, pw),
                    settingsVM
                )
            }
        }
        composable("FTPFileListScreen/{encodedIp}/{encodedUsername}/{encodedPassword}/{port}/{encodedShareName}/{connectionName}") { backStackEntry ->
            val encodedIp = backStackEntry.arguments?.getString("encodedIp")
            val encodedUsername = backStackEntry.arguments?.getString("encodedUsername")
            val encodedPassword = backStackEntry.arguments?.getString("encodedPassword")
            val encodedShareName = backStackEntry.arguments?.getString("encodedShareName")
            val connectionName = backStackEntry.arguments?.getString("connectionName") ?: "未知"
            val port = backStackEntry.arguments?.getString("port") ?: "21"
            //URLEncoder.encode(conn.shareName, "UTF-8")
            if (encodedIp != null) {

                val path = URLDecoder.decode(URLDecoder.decode(encodedIp, "UTF-8"), "UTF-8")
                Log.d(
                    "encodedPath",
                    "${URLDecoder.decode(encodedUsername, "UTF-8")}${
                        URLDecoder.decode(
                            encodedPassword,
                            "UTF-8"
                        )
                    }${URLDecoder.decode(encodedShareName, "UTF-8")}"
                )
                FTPFileListScreen(
                    URLDecoder.decode(encodedShareName, "UTF-8"),
                    mainNavController,
                    FTPConnection(
                        "1",
                        connectionName,
                        ip = encodedIp,
                        port.toSafeInt(21),
                        URLDecoder.decode(encodedUsername, "UTF-8"),
                        URLDecoder.decode(encodedPassword, "UTF-8"),
                        shareName = URLDecoder.decode(encodedShareName, "UTF-8"),
                    ),
                    settingsVM
                )
            }
        }
        composable("NFSFileListScreen/{encodedIp}/{encodedShareName}/{newSubPath}/{connectionName}") { backStackEntry ->
            val encodedIp = backStackEntry.arguments?.getString("encodedIp")
            val encodedShareName = backStackEntry.arguments?.getString("encodedShareName")
            val newSubPath = backStackEntry.arguments?.getString("newSubPath")
            val connectionName = backStackEntry.arguments?.getString("connectionName") ?: "未知"
            //URLEncoder.encode(conn.shareName, "UTF-8")
            if (encodedIp != null) {

                //val path = URLDecoder.decode(URLDecoder.decode(encodedIp, "UTF-8"), "UTF-8")
                Log.d("encodedPath", "${URLDecoder.decode(newSubPath, "UTF-8")}")
                NFSFileListScreen(
                    URLDecoder.decode(newSubPath, "UTF-8"),
                    mainNavController,
                    NFSConnection(
                        "1",
                        connectionName,

                        URLDecoder.decode(encodedIp, "UTF-8"),
                        URLDecoder.decode(encodedShareName, "UTF-8"),

                        ),
                    settingsVM
                )
            }
        }
        composable("HTTPLinkFileListScreen/{connectionName}/{newSubPath}") { backStackEntry ->
            val newSubPath = backStackEntry.arguments?.getString("newSubPath")
            val connectionName = backStackEntry.arguments?.getString("connectionName") ?: "未知"
            //URLEncoder.encode(conn.shareName, "UTF-8")
            if (newSubPath != null) {
                //val path = URLDecoder.decode(URLDecoder.decode(encodedIp, "UTF-8"), "UTF-8")
                Log.d("encodedPath", "${URLDecoder.decode(newSubPath, "UTF-8")}")
                HTTPLinkFileListScreen(
                    URLDecoder.decode(newSubPath, "UTF-8"),
                    mainNavController,
                    connectionName,
                    settingsVM
                )
            }
        }
        composable("MovieDetails/{videoUri}/{dataSourceType}/{fileName}/{connectionName}/{movieId}") { backStackEntry ->
            val videoUri = backStackEntry.arguments?.getString("videoUri") ?: ""
            val dataSourceType = backStackEntry.arguments?.getString("dataSourceType") ?: ""
            val fileName = backStackEntry.arguments?.getString("fileName") ?: ""
            val connectionName = backStackEntry.arguments?.getString("connectionName") ?: ""
            val movieId = backStackEntry.arguments?.getString("movieId")?.toIntOrNull() ?: 0

            MovieDetailsScreen(
                videoUri = videoUri,
                dataSourceType = dataSourceType,
                fileName = fileName,
                connectionName = connectionName,
                movieId = movieId,
                navController = mainNavController
            )
        }
        composable("TVSeriesDetails/{videoUri}/{dataSourceType}/{fileName}/{connectionName}/{seriesId}/{seasonNumber}/{episodeNumber}") { backStackEntry ->
            val videoUri = backStackEntry.arguments?.getString("videoUri") ?: ""
            val dataSourceType = backStackEntry.arguments?.getString("dataSourceType") ?: ""
            val fileName = backStackEntry.arguments?.getString("fileName") ?: ""
            val connectionName = backStackEntry.arguments?.getString("connectionName") ?: ""
            // TV 剧集特有参数
            val seriesId = backStackEntry.arguments?.getString("seriesId")?.toIntOrNull() ?: 0
            val seasonNumber = backStackEntry.arguments?.getString("seasonNumber")?.toIntOrNull() ?: 0
            val episodeNumber = backStackEntry.arguments?.getString("episodeNumber")?.toIntOrNull() ?: 0

            TVSeriesDetailsScreen(
                videoUri = videoUri,
                dataSourceType = dataSourceType,
                fileName = fileName,
                connectionName = connectionName,
                seriesId = seriesId,
                currentSeason = seasonNumber,   // 传递季号
                currentEpisode = episodeNumber, // 传递集号
                navController = mainNavController
            )
        }
        composable("SMBListScreen") {
            SMBConListScreen(mainNavController, smbListViewModel)
        }
        composable("SMBConScreen") {
            SMBConScreen(smbListViewModel)
        }
        composable("WebDavConScreen") {
            WebDavConScreen(webDavListViewModel)
        }
        composable("WebDavListScreen") {
            WebDavConListScreen(mainNavController,webDavListViewModel)
        }
        composable("FTPConScreen") {
            FTPConScreen(ftpListViewModel)
        }
        composable("FTPConListScreen") {
            FTPConListScreen(mainNavController,ftpListViewModel)
        }
        composable("NFSConScreen") {
            NFSConScreen(nfsListViewModel)
        }
        composable("NFSConListScreen") {
            NFSConListScreen(mainNavController,nfsListViewModel)
        }
        composable("HTTPLinkConScreen") {
            HTTPLinkConScreen(httpLinkListViewModel)
        }
        composable("HTTPLinkConListScreen") {
            HTTPLinkConListScreen(mainNavController,httpLinkListViewModel)
        }
        composable("SolarSystemScreen") {
            SolarSystem()
        }
        composable("BlackHoleSimulationScreen") {
            BlackHoleSimulationScreen(modifier = Modifier.fillMaxSize())
        }

    }

}