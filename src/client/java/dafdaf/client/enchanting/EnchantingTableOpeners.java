package dafdaf.client.enchanting;

import net.minecraft.util.math.BlockPos;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.List;
import java.util.ArrayList;

/**
 * 「附魔台共享打开者」登记表：记录哪些玩家正在打开某个位置的附魔台菜单。
 *
 * <p>与 {@link EnchantingTableStorage} 配合实现「共享附魔台」语义：
 * <ul>
 *   <li>打开时登记 → 编辑（放入/拿走）时把权威内容写回所有打开者的槽位（实时共享显示，不复制）；</li>
 *   <li>关闭时注销 → 有其他人打开时只清自己的镜像槽、保留权威；最后一人关闭时按配置存档/归还。</li>
 * </ul>
 *
 * <p>单机（集成服务器同 JVM）下服务端线程读写同一 JVM 的静态表，synchronized 保证可见性；
 * 服务端/客户端分离的联机环境不生效（渲染与共享逻辑同源）。
 */
public final class EnchantingTableOpeners {

	private static final java.util.Map<BlockPos, Set<UUID>> OPENERS = new java.util.HashMap<>();

	private EnchantingTableOpeners() {
	}

	/** 登记指定玩家打开该位置的附魔台。 */
	public static void add(BlockPos pos, UUID playerUuid) {
		synchronized (OPENERS) {
			OPENERS.computeIfAbsent(pos.toImmutable(), p -> new HashSet<>()).add(playerUuid);
		}
	}

	/** 注销指定玩家关闭了该位置的附魔台。 */
	public static void remove(BlockPos pos, UUID playerUuid) {
		synchronized (OPENERS) {
			Set<UUID> set = OPENERS.get(pos.toImmutable());
			if (set != null) {
				set.remove(playerUuid);
				if (set.isEmpty()) {
					OPENERS.remove(pos.toImmutable());
				}
			}
		}
	}

	/** 该位置是否还有其他打开者（排除自己）。 */
	public static boolean hasOtherOpeners(BlockPos pos, UUID selfUuid) {
		synchronized (OPENERS) {
			Set<UUID> set = OPENERS.get(pos.toImmutable());
			if (set == null) {
				return false;
			}
			for (UUID u : set) {
				if (!u.equals(selfUuid)) {
					return true;
				}
			}
			return false;
		}
	}

	/** 该位置所有打开者 UUID（不含空表）。 */
	public static List<UUID> getOpeners(BlockPos pos) {
		synchronized (OPENERS) {
			Set<UUID> set = OPENERS.get(pos.toImmutable());
			return set == null ? List.of() : new ArrayList<>(set);
		}
	}
}
