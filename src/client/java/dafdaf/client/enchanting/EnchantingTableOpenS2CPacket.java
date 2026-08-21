package dafdaf.client.enchanting;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

/**
 * 服务端 → 打开附魔台的玩家：附魔台坐标 + 维度。
 * 客户端收到后写入 {@link OpenEnchantingTableTracker}，供渲染器定位实时 GUI 预览
 * （联机下客户端 handler 的 context 为 EMPTY，唯一来源是此包）。
 */
public record EnchantingTableOpenS2CPacket(BlockPos pos, String dimensionKey) implements CustomPayload {

	public static final CustomPayload.Id<EnchantingTableOpenS2CPacket> ID =
			new CustomPayload.Id<>(Identifier.of("enchantment-table", "table_open"));

	public static final PacketCodec<RegistryByteBuf, EnchantingTableOpenS2CPacket> CODEC =
			new PacketCodec<>() {
				@Override
				public void encode(RegistryByteBuf buf, EnchantingTableOpenS2CPacket pkt) {
					buf.writeBlockPos(pkt.pos);
					buf.writeString(pkt.dimensionKey);
				}

				@Override
				public EnchantingTableOpenS2CPacket decode(RegistryByteBuf buf) {
					return new EnchantingTableOpenS2CPacket(buf.readBlockPos(), buf.readString());
				}
			};

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
