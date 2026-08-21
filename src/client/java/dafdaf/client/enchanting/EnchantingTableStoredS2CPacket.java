package dafdaf.client.enchanting;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;

import net.minecraft.item.ItemStack;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.registry.RegistryOps;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

/**
 * 服务端 → 客户端：某个附魔台位置的「保留材料」记录同步（物品 + 青金石）。
 *
 * <p>物品以 <b>JSON 字符串</b> 传输（{@link ItemStack#CODEC} + 发送方/接收方注册表 ops）：
 * 兼容含自定义附魔的整合包环境，避免二进制 PACKET_CODEC 编码附魔组件时抛
 * 「Can't find id for Reference{...}」断线。
 *
 * <p>发送时机：保存/编辑后广播给追踪该区块的玩家；关闭且清空时广播空记录（清除客户端预览）。
 */
public record EnchantingTableStoredS2CPacket(BlockPos pos, String dimensionKey,
		String itemJson, String lapisJson) implements CustomPayload {

	public static final CustomPayload.Id<EnchantingTableStoredS2CPacket> ID =
			new CustomPayload.Id<>(Identifier.of("enchantment-table", "table_stored"));

	public static String toJson(RegistryOps<JsonElement> ops, ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return "";
		}
		DataResult<JsonElement> result = ItemStack.CODEC.encodeStart(ops, stack);
		return result.result().map(JsonElement::toString).orElse("");
	}

	public static ItemStack fromJson(RegistryOps<JsonElement> ops, String json) {
		if (json == null || json.isEmpty()) {
			return ItemStack.EMPTY;
		}
		try {
			JsonElement element = JsonParser.parseString(json);
			DataResult<ItemStack> result = ItemStack.CODEC.parse(ops, element);
			return result.result().orElse(ItemStack.EMPTY);
		} catch (Exception e) {
			return ItemStack.EMPTY;
		}
	}

	public static EnchantingTableStoredS2CPacket fromData(RegistryOps<JsonElement> ops,
			BlockPos pos, String dimensionKey, ItemStack item, ItemStack lapis) {
		return new EnchantingTableStoredS2CPacket(pos.toImmutable(), dimensionKey,
				toJson(ops, item), toJson(ops, lapis));
	}

	public static final PacketCodec<RegistryByteBuf, EnchantingTableStoredS2CPacket> CODEC =
			new PacketCodec<>() {
				@Override
				public void encode(RegistryByteBuf buf, EnchantingTableStoredS2CPacket pkt) {
					buf.writeBlockPos(pkt.pos);
					buf.writeString(pkt.dimensionKey);
					buf.writeString(pkt.itemJson == null ? "" : pkt.itemJson);
					buf.writeString(pkt.lapisJson == null ? "" : pkt.lapisJson);
				}

				@Override
				public EnchantingTableStoredS2CPacket decode(RegistryByteBuf buf) {
					BlockPos pos = buf.readBlockPos();
					String dimensionKey = buf.readString();
					String itemJson = buf.readString();
					String lapisJson = buf.readString();
					return new EnchantingTableStoredS2CPacket(pos, dimensionKey, itemJson, lapisJson);
				}
			};

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
