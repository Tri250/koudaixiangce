import SwiftUI

struct MasterRecipeDock: View {
    @ObservedObject var model: EditorModel

    var body: some View {
        GeometryReader { geometry in
            let cardHeight = max(66, geometry.size.height - 6)
            let cardWidth: CGFloat = 136
            ScrollView(.horizontal) {
                HStack(spacing: 12) {
                    // None / Off
                    recipeButton(
                        title: "无配方",
                        subtitle: "中性直出",
                        badge: nil,
                        isSelected: model.settings.masterRecipeID == nil,
                        accentGradient: nil,
                        action: { model.selectMasterRecipe(nil) }
                    )
                    .frame(width: 88, height: cardHeight)

                    // Master recipes
                    ForEach(MasterRecipe.allRecipes) { recipe in
                        recipeButton(
                            title: recipe.name,
                            subtitle: recipe.subtitle,
                            badge: "v2 配方",
                            isSelected: model.settings.masterRecipeID == recipe.id,
                            accentGradient: LinearGradient(
                                colors: [
                                    Color(red: 0.35, green: 0.72, blue: 0.88).opacity(0.85),
                                    Color(red: 0.94, green: 0.96, blue: 0.98).opacity(0.9)
                                ],
                                startPoint: .topLeading,
                                endPoint: .bottomTrailing
                            ),
                            action: { model.selectMasterRecipe(recipe) }
                        )
                        .frame(width: cardWidth, height: cardHeight)
                    }
                }
                .padding(.horizontal, 16)
                .frame(minWidth: geometry.size.width)
            }
            .scrollIndicators(.visible)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .tint(.yellow)
    }

    private func recipeButton(
        title: String,
        subtitle: String,
        badge: String?,
        isSelected: Bool,
        accentGradient: LinearGradient?,
        action: @escaping () -> Void
    ) -> some View {
        Button(action: action) {
            VStack(alignment: .leading, spacing: 4) {
                HStack {
                    if let gradient = accentGradient {
                        Circle()
                            .fill(gradient)
                            .frame(width: 14, height: 14)
                            .overlay(Circle().strokeBorder(Color.white.opacity(0.4), lineWidth: 0.5))
                    } else {
                        Image(systemName: "circle.slash")
                            .font(.system(size: 11))
                            .foregroundStyle(.secondary)
                    }
                    Text(title)
                        .font(.system(size: 12, weight: isSelected ? .bold : .medium))
                        .lineLimit(1)
                    Spacer()
                    if let badge {
                        Text(badge)
                            .font(.system(size: 9, weight: .semibold))
                            .padding(.horizontal, 4)
                            .padding(.vertical, 1)
                            .background(Color.yellow.opacity(isSelected ? 0.25 : 0.12), in: Capsule())
                            .foregroundStyle(isSelected ? Color.yellow : Color.secondary)
                    }
                }
                Text(subtitle)
                    .font(.system(size: 10))
                    .foregroundStyle(.secondary)
                    .lineLimit(2)
            }
            .padding(8)
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
            .contentShape(Rectangle())
            .background(
                isSelected ? Color.yellow.opacity(0.08) : Color.white.opacity(0.03),
                in: RoundedRectangle(cornerRadius: 6)
            )
            .overlay {
                RoundedRectangle(cornerRadius: 6)
                    .strokeBorder(isSelected ? Color.yellow : Color.white.opacity(0.12), lineWidth: 1)
            }
        }
        .buttonStyle(.plain)
        .accessibilityLabel(title)
        .accessibilityValue(isSelected ? "已选中" : "未选中")
        .accessibilityAddTraits(isSelected ? .isSelected : [])
    }
}
