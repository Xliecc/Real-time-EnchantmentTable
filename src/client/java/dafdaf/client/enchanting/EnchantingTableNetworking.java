package dafdaf.client.enchanting;

import com.google.gson.JsonElement;

import com.mojang.serialization.JsonOps;

import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryOps;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

/**
 * 附魔台保留材料的服务端发包入口（S2C）。
 *
 * <p>单机（集成服务器）与联机（LAN / 独立服务器）走完全相同的 Fabric Networking 通道——
 * 集成服务器对本地玩家同样存在一条内部网络连接，{@code ServerPlayNetworking.send} 对两者
 * 行为一致，无需分支。
 */
public final class EnchantingTableNetworking {

	private EnchantingTableNetworking() {
	}

	/** 把「刚打开的附魔台坐标」发给开桌玩家本人（客户端写入 tracker 定位实时预览）。 */
	public static void sendOpen(ServerPlayerEntity player, BlockPos pos, String dimensionKey) {
		ServerPlayNetworking.send(player, new EnchantingTableOpenS2CPacket(pos.toImmutable(), dimensionKey));
	}

	/** 广播该位置的保留记录（或空记录 = 清除预览）给追踪该区块的所有玩家。 */
	public static void broadcastStored(ServerWorld world, BlockPos pos, String dimensionKey,
			ItemStack item, ItemStack lapis) {
		RegistryOps<JsonElement> ops = RegistryOps.of(JsonOps.INSTANCE, world.getRegistryManager());
		EnchantingTableStoredS2CPacket payload =
				EnchantingTableStoredS2CPacket.fromData(ops, pos, dimensionKey, item, lapis);
		for (ServerPlayerEntity watcher : PlayerLookup.tracking(world, pos)) {
			ServerPlayNetworking.send(watcher, payload);
		}
	}
}
