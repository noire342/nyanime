package eu.kanade.presentation.more

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.theme.NyanimeLogoIcon
import eu.kanade.presentation.theme.NyanimeWordmark

@Composable
fun LogoHeader() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        NyanimeLogoIcon(
            modifier = Modifier
                .padding(top = 32.dp, bottom = 8.dp)
                .size(64.dp),
        )

        NyanimeWordmark(Modifier.padding(bottom = 24.dp))
        HorizontalDivider()
    }
}
