package org.mz.mzdkplayer.ui.screen.setting

// PerformanceTestScreen.kt



import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import org.mz.mzdkplayer.R // 请确保 ic_insert, ic_clear_db 资源存在
import org.mz.mzdkplayer.di.RepositoryProvider
import org.mz.mzdkplayer.tool.viewModelWithFactory
import org.mz.mzdkplayer.ui.screen.common.LoadingScreen
import org.mz.mzdkplayer.ui.screen.common.MyIconButton
import org.mz.mzdkplayer.ui.screen.vm.PerformanceTestViewModel


@Composable
fun PerformanceTestScreen(
    viewModel: PerformanceTestViewModel =viewModelWithFactory {
        RepositoryProvider.createPerformanceTestViewModel()
    }
) {
    val status by viewModel.status.collectAsState()
    val isInserting by viewModel.isInserting.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = stringResource(R.string.ui_label_database_performance_test_tool),
            style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.padding(bottom = 40.dp)
        )

        if (isInserting) {
            LoadingScreen(modifier = Modifier.size(64.dp))
            Spacer(modifier = Modifier.height(24.dp))
        }

        // --- 插入数据按钮 ---
        MyIconButton(
            text = stringResource(R.string.ui_label_insert_50000_dummy_records),
            icon = R.drawable.baseline_search_24, // 假设 R.drawable.ic_search 是一个通用图标
            onClick = { viewModel.startInsertion(50000) },
            enabled = !isInserting,
            modifier = Modifier.width(300.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))

        // --- 清理数据库按钮 ---
        MyIconButton(
            text = stringResource(R.string.ui_label_clear_all_cached_data),
            icon = R.drawable.close24dp, // 假设 R.drawable.ic_clear 是一个通用图标
            onClick = { viewModel.clearDatabase() },
            enabled = !isInserting,
            modifier = Modifier.width(300.dp)
        )

        Spacer(modifier = Modifier.height(40.dp))

        // --- 状态显示 ---
        Text(
            text = stringResource(R.string.ui_label_current_status, status),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}