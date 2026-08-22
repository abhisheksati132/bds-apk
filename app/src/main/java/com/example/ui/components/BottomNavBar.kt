package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Update
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.Update
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.viewmodel.MainTab

data class NavItem(
    val tab: MainTab,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

@Composable
fun CleanBottomNavBar(
    activeTab: MainTab,
    onTabSelected: (MainTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val navItems = listOf(
        NavItem(
            tab = MainTab.CHATS,
            label = "Chats",
            selectedIcon = Icons.Filled.Chat,
            unselectedIcon = Icons.Outlined.Chat,
            testTag = "nav_chats_tab"
        ),
        NavItem(
            tab = MainTab.STATUS,
            label = "Status",
            selectedIcon = Icons.Filled.Update,
            unselectedIcon = Icons.Outlined.Update,
            testTag = "nav_status_tab"
        ),
        NavItem(
            tab = MainTab.CALLS,
            label = "Calls",
            selectedIcon = Icons.Filled.Phone,
            unselectedIcon = Icons.Outlined.Phone,
            testTag = "nav_calls_tab"
        ),
        NavItem(
            tab = MainTab.VAULT_SECURITY,
            label = "Vault",
            selectedIcon = Icons.Filled.Lock,
            unselectedIcon = Icons.Outlined.Lock,
            testTag = "nav_vault_tab"
        )
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        HorizontalDivider(
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
            thickness = 0.8.dp
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .height(68.dp)
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            navItems.forEach { item ->
                val isSelected = item.tab == activeTab
                val pillBgColor by animateColorAsState(
                    targetValue = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                    label = "pill_bg"
                )
                val iconColor by animateColorAsState(
                    targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                    label = "icon_color"
                )
                val textColor = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant

                Column(
                    modifier = Modifier
                        .testTag(item.testTag)
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = ripple()
                        ) { onTabSelected(item.tab) }
                        .padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .width(56.dp)
                            .height(30.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(pillBgColor),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isSelected) item.selectedIcon else item.unselectedIcon,
                            contentDescription = item.label,
                            tint = iconColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = item.label,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = textColor
                    )
                }
            }
        }
    }
}
