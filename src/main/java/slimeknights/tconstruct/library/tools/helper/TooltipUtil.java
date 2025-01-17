package slimeknights.tconstruct.library.tools.helper;

import net.minecraft.entity.ai.attributes.Attributes;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TextFormatting;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.common.TinkerTags;
import slimeknights.tconstruct.library.materials.MaterialRegistry;
import slimeknights.tconstruct.library.materials.definition.IMaterial;
import slimeknights.tconstruct.library.modifiers.ModifierEntry;
import slimeknights.tconstruct.library.tools.item.IModifiable;
import slimeknights.tconstruct.library.tools.item.IModifiableDisplay;
import slimeknights.tconstruct.library.tools.nbt.IModifierToolStack;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;
import slimeknights.tconstruct.library.tools.part.IToolPart;
import slimeknights.tconstruct.library.tools.stat.ToolStats;
import slimeknights.tconstruct.library.utils.TooltipFlag;
import slimeknights.tconstruct.library.utils.TooltipKey;

import java.util.List;

/** Helper functions for adding tooltips to tools */
public class TooltipUtil {
  /** Tool tag to set that makes a tool a display tool */
  public static final String KEY_DISPLAY = "tic_display";

  private TooltipUtil() {}

  /** Tooltip telling the player to hold shift for more info */
  public static final ITextComponent TOOLTIP_HOLD_SHIFT = TConstruct.makeTranslation("tooltip", "hold_shift", TConstruct.makeTranslation("key", "shift").mergeStyle(TextFormatting.YELLOW, TextFormatting.ITALIC));
  /** Tooltip telling the player to hold control for part info */
  public static final ITextComponent TOOLTIP_HOLD_CTRL = TConstruct.makeTranslation("tooltip", "hold_ctrl", TConstruct.makeTranslation("key", "ctrl").mergeStyle(TextFormatting.AQUA, TextFormatting.ITALIC));
  /** Tooltip for when tool data is missing */
  private static final ITextComponent NO_DATA = TConstruct.makeTranslation("tooltip", "missing_data").mergeStyle(TextFormatting.GRAY);

  /**
   * If true, this stack was created for display, so some of the tooltip is suppressed
   * @param stack  Stack to check
   * @return  True if marked display
   */
  public static boolean isDisplay(ItemStack stack) {
    CompoundNBT nbt = stack.getTag();
    return nbt != null && nbt.getBoolean(KEY_DISPLAY);
  }

  /**
   * Full logic for adding tooltip information
   */
  public static void addInformation(IModifiableDisplay item, ItemStack stack, List<ITextComponent> tooltip, TooltipKey tooltipKey, boolean isAdvanced) {
    // if the display tag is set, just show modifiers
    if (isDisplay(stack)) {
      ToolStack tool = ToolStack.from(stack);
      for (ModifierEntry entry : tool.getModifierList()) {
        if (entry.getModifier().shouldDisplay(false)) {
          tooltip.add(entry.getModifier().getDisplayName(tool, entry.getLevel()));
        }
      }
      // if not initialized, show no data tooltip on non-standard items
    } else if (!ToolStack.isInitialized(stack)) {
      if (item.getToolDefinition().isMultipart()) {
        tooltip.add(NO_DATA);
      }
    } else {
      switch (tooltipKey) {
        case SHIFT:
          item.getStatInformation(ToolStack.from(stack), tooltip, isAdvanced ? TooltipFlag.ADVANCED : TooltipFlag.NORMAL);
          break;
        case CONTROL:
          if (item.getToolDefinition().isMultipart()) {
            TooltipUtil.getComponents(item, stack, tooltip);
            break;
          }
          // intentional fallthrough
        default:
          getDefaultInfo(stack, tooltip);
          break;
      }
    }
  }

  /**
   * Adds information when holding neither control nor shift
   * @param stack     Stack instance
   * @param tooltips  Tooltip list
   */
  public static void getDefaultInfo(ItemStack stack, List<ITextComponent> tooltips) {
    ToolStack tool = ToolStack.from(stack);
    // shows as broken when broken, hold shift for proper durability
    if (stack.isDamageable()) {
      tooltips.add(TooltipBuilder.formatDurability(tool.getCurrentDurability(), tool.getStats().getInt(ToolStats.DURABILITY), true));
    }
    // modifier tooltip
    for (ModifierEntry entry : tool.getModifierList()) {
      if (entry.getModifier().shouldDisplay(false)) {
        tooltips.add(entry.getModifier().getDisplayName(tool, entry.getLevel()));
      }
    }
    tooltips.add(StringTextComponent.EMPTY);
    tooltips.add(TOOLTIP_HOLD_SHIFT);
    if (tool.getDefinition().isMultipart()) {
      tooltips.add(TOOLTIP_HOLD_CTRL);
    }
  }

  /**
   * Gets the  default information for the given tool stack
   *
   * @param tool      the tool stack
   * @param tooltip   Tooltip list
   * @param flag      Tooltip flag
   * @return List from the parameter after filling
   */
  public static List<ITextComponent> getDefaultStats(IModifierToolStack tool, List<ITextComponent> tooltip, TooltipFlag flag) {
    TooltipBuilder builder = new TooltipBuilder(tool, tooltip);
    Item item = tool.getItem();
    if (TinkerTags.Items.DURABILITY.contains(item)) {
      builder.addDurability();
    }
    if (TinkerTags.Items.MELEE.contains(item)) {
      builder.addWithAttribute(ToolStats.ATTACK_DAMAGE, Attributes.ATTACK_DAMAGE);
      builder.add(ToolStats.ATTACK_SPEED);
    }
    if (TinkerTags.Items.HARVEST.contains(item)) {
      if (TinkerTags.Items.HARVEST_PRIMARY.contains(tool.getItem())) {
        builder.add(ToolStats.HARVEST_LEVEL);
      }
      builder.add(ToolStats.MINING_SPEED);
    }

    builder.addAllFreeSlots();

    for (ModifierEntry entry : tool.getModifierList()) {
      entry.getModifier().addInformation(tool, entry.getLevel(), tooltip, flag);
    }

    return builder.getTooltips();
  }

  /**
   * Gets the tooltip of the components list of a tool
   * @param item      Modifiable item instance
   * @param stack     Item stack being displayed
   * @param tooltips  List of tooltips
   */
  public static void getComponents(IModifiable item, ItemStack stack, List<ITextComponent> tooltips) {
    // no components, nothing to do
    List<IToolPart> components = item.getToolDefinition().getRequiredComponents();
    if (components.isEmpty()) {
      return;
    }
    // no materials is bad
    List<IMaterial> materials = ToolStack.from(stack).getMaterialsList();
    if (materials.isEmpty()) {
      tooltips.add(NO_DATA);
      return;
    }
    // wrong number is bad
    if (materials.size() < components.size()) {
      return;
    }
    // finally, display them all
    int max = components.size() - 1;
    for (int i = 0; i <= max; i++) {
      IToolPart requirement = components.get(i);
      IMaterial material = materials.get(i);
      ItemStack partStack = requirement.withMaterial(material);
      tooltips.add(partStack.getDisplayName().deepCopy().mergeStyle(TextFormatting.UNDERLINE).modifyStyle(style -> style.setColor(material.getColor())));
      MaterialRegistry.getInstance().getMaterialStats(material.getIdentifier(), requirement.getStatType()).ifPresent(stat -> tooltips.addAll(stat.getLocalizedInfo()));
      if (i != max) {
        tooltips.add(StringTextComponent.EMPTY);
      }
    }
  }
}
