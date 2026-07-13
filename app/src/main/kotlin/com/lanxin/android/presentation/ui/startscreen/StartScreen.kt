package com.lanxin.android.presentation.ui.startscreen

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.lanxin.android.presentation.common.PrimaryLongButton
import com.lanxin.android.presentation.icons.GptMobileStartScreen

@Composable
fun StartScreen(onStartClick: () -> Unit) {
    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            StartScreenLogo()
            Spacer(modifier = Modifier.weight(1f))
            WelcomeText()
            PrimaryLongButton(
                onClick = onStartClick,
                text = "开始使用"
            )
        }
    }
}

@Preview
@Composable
fun StartScreenLogo(modifier: Modifier = Modifier) {
    Image(
        imageVector = GptMobileStartScreen,
        contentDescription = "兰心启动页图标",
        contentScale = ContentScale.FillHeight,
        modifier = modifier
            .padding(top = 50.dp)
            .height(400.dp)
    )
}

@Preview
@Composable
fun WelcomeText(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(20.dp)
    ) {
        Text(
            modifier = Modifier
                .padding(4.dp)
                .semantics { heading() },
            text = "欢迎使用兰心",
            style = MaterialTheme.typography.headlineMedium
        )
        Text(
            modifier = Modifier.padding(4.dp),
            text = "你的专属 AI 助手，随时陪伴，有问必答",
            style = MaterialTheme.typography.bodyLarge
        )
    }
}
