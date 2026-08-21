package dafdaf.client.enchanting;

import dafdaf.client.config.PreviewConfig;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.EnchantmentScreen;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.block.entity.state.EnchantingTableBlockEntityRenderState;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.item.ItemRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.screen.EnchantmentScreenHandler;
import net.minecraft.util.Util;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.LightType;

public final class EnchantingPreviewRenderer {
   private static final double BOOK_X = 0.5;
   private static final double BOOK_Y = 0.93;
   private static final double BOOK_Z = 0.5;
   private static final double EMERGE_ARC = 0.22;
   /** 物品悬浮高度的默认值（与 PreviewConfig.itemHeight 默认一致）。
    *  青金石轨道锚定在「物品处于默认悬浮高度」的水平面，使其独立于当前 itemHeight：
    *  用户调物品高度时，物品移动、青金石轨道保持稳定。 */
   private static final double DEFAULT_ITEM_HEIGHT = 0.75;
   private static final int MAX_LAPIS_PIECES = 3;
   private static final double BOOK_OPEN_RANGE = 3.0;
   private static final double MAX_RENDER_DISTANCE = 63.0;
   private static final double MAX_RENDER_DISTANCE_SQ = MAX_RENDER_DISTANCE * MAX_RENDER_DISTANCE;
   private static final Map<BlockPos, EnchantingPreviewRenderer.TableAnimState> ANIM_STATE = new HashMap<>();
   private static final Set<BlockPos> TOUCHED = new HashSet<>();
   private static final Set<BlockPos> BER_RENDERED = new HashSet<>();
   private static final Set<BlockPos> KEEP = new HashSet<>();
   private static final Map<BlockPos, Long> BURSTS = new HashMap<>();

   public static void triggerEnchantBurst(BlockPos pos) {
      synchronized (BURSTS) {
         BURSTS.put(pos.toImmutable(), Util.getMeasuringTimeMs());
      }
   }

   private EnchantingPreviewRenderer() {
   }

   public static void render(WorldRenderContext context) {
      MinecraftClient client = MinecraftClient.getInstance();
      ClientWorld world = client.world;
      Entity cameraEntity = client.getCameraEntity();
      if (world != null && cameraEntity != null) {
         Camera camera = client.gameRenderer.getCamera();
         float tickDelta = client.getRenderTickCounter().getTickProgress(true);
         OrderedRenderCommandQueue queue = context.commandQueue();
         MatrixStack matrices = context.matrices();
         if (queue != null && matrices != null) {
            double time = Util.getMeasuringTimeMs() % 1000000000L / 50.0;
            PreviewConfig cfg = PreviewConfig.get();
            if (cfg.modToggle != PreviewConfig.ModToggle.NO) {
               TOUCHED.clear();
               BlockPos guiPos = null;
               ItemStack guiItem = ItemStack.EMPTY;
               ItemStack guiLapis = ItemStack.EMPTY;
               if (client.currentScreen instanceof EnchantmentScreen screen && screen.getScreenHandler() instanceof EnchantmentScreenHandler handler) {
                  BlockPos hit = OpenEnchantingTableTracker.get();
                  if (hit != null && world.getBlockState(hit).getBlock() == Blocks.ENCHANTING_TABLE) {
                     guiPos = hit;
                     guiItem = handler.getSlot(0).getStack();
                     guiLapis = handler.getSlot(1).getStack();
                     if (guiItem.isEmpty() && guiLapis.isEmpty()) {
                        EnchantingTableStorage.TableData d = EnchantingTableStorage.peekClient(world, hit);
                        if (d != null && !d.isEmpty()) {
                           guiItem = d.item();
                           guiLapis = d.lapis();
                        }
                     }
                  }
               }

               {
                  for (EnchantingTableStorage.StoredPreview stored : EnchantingTableStorage.peekAll(world)) {
                     if (!stored.pos().equals(guiPos)
                        && !BER_RENDERED.contains(stored.pos())
                        && world.getBlockState(stored.pos()).getBlock() == Blocks.ENCHANTING_TABLE) {
                        renderPreview(
                           matrices,
                           queue,
                           world,
                           camera,
                           client,
                           cameraEntity,
                           cfg,
                           time,
                           tickDelta,
                           stored.pos(),
                           stored.data().item(),
                           stored.data().lapis(),
                           false,
                           false
                        );
                     }
                  }
               }

               if (guiPos != null && !BER_RENDERED.contains(guiPos)) {
                  renderPreview(matrices, queue, world, camera, client, cameraEntity, cfg, time, tickDelta, guiPos, guiItem, guiLapis, true, false);
               }

               // 复用静态 set 做 keep 交集（避免每帧分配 HashSet），清理已消失台的动画状态。
               KEEP.clear();
               KEEP.addAll(TOUCHED);
               KEEP.addAll(BER_RENDERED);
               ANIM_STATE.keySet().retainAll(KEEP);
               LIGHT_CACHE.keySet().retainAll(KEEP);
               BER_RENDERED.clear();
            }
         }
      }
   }

   public static void renderBer(EnchantingTableBlockEntityRenderState state, MatrixStack matrices, OrderedRenderCommandQueue queue) {
      MinecraftClient client = MinecraftClient.getInstance();
      ClientWorld world = client.world;
      Entity cameraEntity = client.getCameraEntity();
      if (world != null && cameraEntity != null) {
         if (PreviewConfig.get().modToggle != PreviewConfig.ModToggle.NO) {
            BlockPos tablePos = state.pos;
            if (world.getBlockState(tablePos).getBlock() == Blocks.ENCHANTING_TABLE) {
               float tickDelta = client.getRenderTickCounter().getTickProgress(true);
               double time = Util.getMeasuringTimeMs() % 1000000000L / 50.0;
               PreviewConfig cfg = PreviewConfig.get();
               ItemStack item = ItemStack.EMPTY;
               ItemStack lapis = ItemStack.EMPTY;
               BlockPos guiPos = OpenEnchantingTableTracker.get();
               if (guiPos != null
                  && guiPos.equals(tablePos)
                  && client.currentScreen instanceof EnchantmentScreen screen
                  && screen.getScreenHandler() instanceof EnchantmentScreenHandler handler) {
                  item = handler.getSlot(0).getStack();
                  lapis = handler.getSlot(1).getStack();
                  // GUI 打开时槽位可能尚未同步（restore 竞态）：若槽位为空但渲染缓存有物品则用它兜底显示，
                  // 避免「物品在 GUI 但预览已播放退场」；手动清空槽位时 onContentChanged 会更新缓存为空、
                  // 兜底自然失效，不会误显示。
                  if (item.isEmpty() && lapis.isEmpty()) {
                     EnchantingTableStorage.TableData d = EnchantingTableStorage.peekClient(world, tablePos);
                     if (d != null && !d.isEmpty()) {
                        item = d.item();
                        lapis = d.lapis();
                     }
                  }
               } else {
                  // 无论 keep，渲染层都从权威缓存显示已存物品：切换 keep 后物品仍保留在台上，
                  // 不会被立即判定消失而播放退场动画；keep 只决定打开恢复/关闭带出（带出时缓存被 take 清空）。
                  EnchantingTableStorage.TableData data = EnchantingTableStorage.peekClient(world, tablePos);
                  if (data != null) {
                     item = data.item();
                     lapis = data.lapis();
                  }
               }

               BER_RENDERED.add(tablePos);
               boolean berGuiActive = guiPos != null
                  && guiPos.equals(tablePos)
                  && client.currentScreen instanceof EnchantmentScreen
                  && ((EnchantmentScreen)client.currentScreen).getScreenHandler() instanceof EnchantmentScreenHandler;
               renderPreview(matrices, queue, world, null, client, cameraEntity, cfg, time, tickDelta, tablePos, item, lapis, berGuiActive, true);
            }
         }
      }
   }

   private static void renderPreview(
      MatrixStack matrices,
      OrderedRenderCommandQueue queue,
      ClientWorld world,
      Camera camera,
      MinecraftClient client,
      Entity cameraEntity,
      PreviewConfig cfg,
      double time,
      float tickDelta,
      BlockPos tablePos,
      ItemStack item,
      ItemStack lapis,
      boolean guiActive,
      boolean berContext
   ) {
      TOUCHED.add(tablePos);
      // 渲染距离上限：超过 64 格（约等于原版方块实体剔除距离）完全不渲染、也完全不计算
      // （快速返回，跳过动画推进/光照/矩阵）。视觉在远处本就消失，这里让计算一起停掉。
      if (cameraEntity != null
            && tablePos.getSquaredDistance(cameraEntity.getBlockPos().getX(), cameraEntity.getBlockPos().getY(), cameraEntity.getBlockPos().getZ())
                  > MAX_RENDER_DISTANCE_SQ) {
         return;
      }
      EnchantingPreviewRenderer.TableAnimState st = ANIM_STATE.computeIfAbsent(tablePos, pxx -> new EnchantingPreviewRenderer.TableAnimState());
      double now = time + tickDelta;
      double dt = st.lastFrameTicks < 0.0 ? 0.0 : MathHelper.clamp(now - st.lastFrameTicks, 0.0, 2.0);
      st.lastFrameTicks = now;
      double smoothTick = (float)world.getTime() + tickDelta;
      // 「与书本同步」已内置为固定行为：距台中心 3 格内（排除旁观者）显示拦浮预览，走远合书收回。
      boolean bookOpen = world.getClosestPlayer(tablePos.getX() + 0.5, tablePos.getY() + 0.5, tablePos.getZ() + 0.5, 3.0, false) != null;
      boolean opening = bookOpen && !st.wasOpen;
      st.wasOpen = bookOpen;
      boolean hasItem = item != null && !item.isEmpty();
      boolean itemVisible = hasItem && bookOpen;
      boolean animationVisible = itemVisible;
      if (guiActive && !itemVisible && st.hadItemVisible) {
         st.needReplay = true;
      }

      st.hadItemVisible = itemVisible;
      if (st.wasItemVisible != itemVisible) {
         st.itemStartAt = now;
      }

      st.wasItemVisible = itemVisible;

      // 快速短路：书合上 + 无物品显示 + 退场动画已播完 + 无粒子爆发 + 非 GUI ——
      // 这台完全静止，跳过 schedulePieces / computeLight / 绘制（每帧省去无谓的动画与光照计算）。
      // 顶部 transition 状态（wasOpen/wasItemVisible）已在上方更新，重开会正常触发入场，不破坏逻辑。
      boolean idleInactive = !bookOpen && !itemVisible && st.itemProgress <= 0.0F
            && st.pieces.isEmpty() && !st.needReplay && !guiActive;
      if (idleInactive && cfg.enchantFxStyle != PreviewConfig.EnchantFxStyle.OFF) {
         synchronized (BURSTS) { if (BURSTS.get(tablePos) != null) idleInactive = false; }
      }
      if (idleInactive) {
         return;
      }

      int rawLapisTarget = bookOpen && lapis != null && !lapis.isEmpty() ? Math.min(lapis.getCount(), 3) : 0;
      int lapisTarget;
      if (rawLapisTarget == 0 && bookOpen && st.lastLapisTarget > 0 && st.lapisZeroHoldFrames < 4) {
         st.lapisZeroHoldFrames++;
         lapisTarget = st.lastLapisTarget;
      } else {
         st.lapisZeroHoldFrames = 0;
         lapisTarget = rawLapisTarget;
      }

      st.lastLapisTarget = lapisTarget;
      if (lapis != null && !lapis.isEmpty() && lapisTarget > 0) {
         st.lastLapis = lapis.copyWithCount(1);
      }

      double lapisBaseDelay = 0.0;
      ItemStack normalizedItem = item != null && !item.isEmpty() ? item.copyWithCount(1) : ItemStack.EMPTY;
      if (!itemVisible || !st.needReplay && samePreviewItem(st.lastItem, normalizedItem)) {
         if (itemVisible
            && !st.lastItem.isEmpty()
            && sameBaseItemIgnoringEnchantments(st.lastItem, normalizedItem)
            && !sameEnchantments(st.lastItem, normalizedItem)) {
            st.lastItem = normalizedItem.copy();
         } else if (opening && itemVisible && st.itemProgress <= 0.0F && st.itemStartAt < now) {
            st.itemStartAt = now;
            st.returnExtraDeg = 0.0;
            st.spinDegPerSec = 540.0;
            st.itemSpinAccum = 0.0;
            lapisBaseDelay = cfg.emergeStaggerSeconds * 5.0;
         }
      } else {
         st.needReplay = false;
         st.lastItem = normalizedItem.copy();
         st.itemProgress = 0.0F;
         st.itemStartAt = now;
         st.returnExtraDeg = 0.0;
			st.spinDegPerSec = 540.0;
			st.itemSpinAccum = 0.0;
         lapisBaseDelay = cfg.emergeStaggerSeconds * 5.0;
      }

      // 帧率无关：动画推进一律用「真实墙钟经过时间」（dt，单位刻），不依赖每帧固定增量。
      // 墙钟在游戏暂停（GUI 打开）时也推进，动画不会冻住；smoothTick 仅用于旋转/起伏
      // （游戏时间本身与帧率无关）。
      double speed = dt;
      st.lastSmoothTick = smoothTick;
      st.lastWallFrameTicks = now;
      if (!cfg.animEnabled) {
         st.itemProgress = animationVisible ? 1.0F : 0.0F;
      } else {
         st.itemProgress = animationVisible
            ? Math.min(1.0F, st.itemProgress + (float)(speed / (cfg.itemEmergeSeconds * 20.0)))
            : Math.max(0.0F, st.itemProgress - (float)(speed / (cfg.itemEmergeOutSeconds * 20.0)));
      }

      schedulePieces(st, lapisTarget, now, speed, lapisBaseDelay, cfg);
      int light = computeLight(world, cfg, tablePos);
      matrices.push();

      try {
         if (!berContext) {
            translateToBlock(matrices, camera, tablePos);
         }

         double itemBase = 0.75 + cfg.itemHeight;
         // 青金石轨道锚定在「物品默认悬浮高度」的水平面，独立于当前 itemHeight——
         // 用户调物品高度只移动物品本身，青金石轨道保持稳定。
         double lapisBase = 0.75 + DEFAULT_ITEM_HEIGHT + cfg.lapisHeight;
         if (st.itemProgress > 0.0F && now >= st.itemStartAt && !st.lastItem.isEmpty()) {
            boolean entering = animationVisible;
            float stageSecondsF = (float)(entering ? cfg.itemEmergeSeconds : cfg.itemEmergeOutSeconds);
            if (st.displayProgress < 0.0F) {
               st.displayProgress = MathHelper.clamp(st.itemProgress, 0.0F, 1.0F);
               st.hadEntering = entering;
            } else if (st.hadEntering != entering) {
               float curE = st.lastVisualE;
               if (entering) {
                  st.displayProgress = 1.0F - (float)Math.sqrt(Math.max(0.0, 1.0 - curE));
               } else {
                  st.displayProgress = (float)Math.sqrt(Math.max(0.0, curE));
               }

               st.hadEntering = entering;
            }

            float dDisp = (float)(speed / (stageSecondsF * 20.0));
            if (entering) {
               st.displayProgress = Math.min(1.0F, st.displayProgress + dDisp);
            } else {
               st.displayProgress = Math.max(0.0F, st.displayProgress - dDisp);
            }

            float e = entering ? 1.0F - (1.0F - st.displayProgress) * (1.0F - st.displayProgress) : st.displayProgress * st.displayProgress;
            st.lastVisualE = e;
            double phase = smoothTick * radPerTick(cfg.floatSeconds);
            double bob = Math.sin(phase) * cfg.floatAmplitude;
            double py = MathHelper.lerp(e, BOOK_Y, itemBase + bob) + arcBump(e) * EMERGE_ARC;
            float scaleMul = 0.2F + 0.8F * e;
            double p0 = MathHelper.clamp(st.itemProgress, 0.0F, 1.0F);
            // 「边出来边旋转」：入场转速与位置缓出曲线同频（开头快、随位置渐慢），
            // 确保物品飞出时就在转；到位后线性摩擦滑停到基准匀速自转（连贯无突降）。
            // 峰值由「圈数 ÷ 阶段时长」得出——emergeSpinTurns / itemEmergeOutSpinTurns 配置直接控制圈数。
            double frameSec = Math.max(0.0, dt) / 20.0;
            double spinTurns = animationVisible ? cfg.emergeSpinTurns : cfg.itemEmergeOutSpinTurns;
            double stageSec = animationVisible ? cfg.itemEmergeSeconds : cfg.itemEmergeOutSeconds;
            double entryVel = Math.max(0.0, spinTurns) * 360.0 / Math.max(0.1, stageSec);
            double baseVel = 360.0 / cfg.rotationSeconds;
            double xp = st.displayProgress >= 0.0F ? MathHelper.clamp(st.displayProgress, 0.0F, 1.0F) : p0;
            if (p0 < 1.0) {
               // 位置缓出曲线导数：入口开头快随位置渐慢；下限保留明显余速（≥基准且 >0.35 峰值），
               // 保证到位后仍有速度可作惯性滑停，而非瞬间变匀速。
               double minPosVel = Math.max(baseVel / Math.max(entryVel, 1.0), 0.35);
               double posVel = animationVisible
                     ? Math.max(minPosVel, 2.0 * (1.0 - xp))
                     : 2.0 * xp;
               st.spinDegPerSec = entryVel * posVel;
            } else if (st.spinDegPerSec > baseVel) {
               // 线性摩擦减速（物理惯性）：每帧减去固定角速度（较缓），速度均匀下降，
               // 从入场余速慢慢滑停到基准匀速自转，惯性感明显、无瞬间缓速。
               st.spinDegPerSec = Math.max(baseVel, st.spinDegPerSec - 100.0 * frameSec);
            }
            st.itemSpinAccum += st.spinDegPerSec * frameSec;
            double angleDeg = st.itemSpinAccum;
            renderItem(matrices, queue, st.lastItem, 0.5, py, 0.5, (float)(cfg.itemScale * scaleMul), angleDeg, light, client, cameraEntity);
         }

         if (!st.pieces.isEmpty() && !st.lastLapis.isEmpty()) {
            double orbitPhaseDeg = smoothTick * (360.0 / (cfg.orbitSeconds * 20.0));
            int activeN = 0;

            for (EnchantingPreviewRenderer.LapisPiece p : st.pieces) {
               if (!p.returning) {
                  activeN++;
               }
            }

            int idx = 0;

            for (EnchantingPreviewRenderer.LapisPiece p : st.pieces) {
               if (!p.returning) {
                  double targetOffset = activeN <= 1 ? 0.0 : idx * (360.0 / activeN);
                  double delta = MathHelper.wrapDegrees(targetOffset - p.orbitOffsetDeg);
                  p.orbitOffsetDeg = p.orbitOffsetDeg + delta * (1.0 - Math.exp(-dt / 6.0));
                  idx++;
               }
            }

            int pieceIdx = 0;

            for (EnchantingPreviewRenderer.LapisPiece p : st.pieces) {
               boolean waiting = !p.returning && now < p.startAtTicks;
               if (!waiting && !(p.progress <= 0.0F)) {
                  double orbitAngleDeg = orbitPhaseDeg + p.orbitOffsetDeg;
                  double rad = Math.toRadians(orbitAngleDeg);
                  double bobPhase = (smoothTick + pieceIdx * 10.0) * radPerTick(cfg.lapisFloatSeconds);
                  double bob = Math.sin(bobPhase) * cfg.lapisFloatAmplitude;
                  double ox = 0.5 + Math.sin(rad) * cfg.orbitRadius;
                  double oz = 0.5 + Math.cos(rad) * cfg.orbitRadius;
                  double oy = lapisBase + bob;
                  double px = ox;
                  double py = oy;
                  double pz = oz;
                  float scaleMul = 1.0F;
                  if (true) {
                     boolean entering = !p.returning;
                     float stageSecondsF = (float)(entering ? cfg.emergeSeconds : cfg.emergeOutSeconds);
                     if (p.displayProgress < 0.0F) {
                        p.displayProgress = MathHelper.clamp(p.progress, 0.0F, 1.0F);
                        p.hadEntering = entering;
                     } else if (p.hadEntering != entering) {
                        float curE = p.lastVisualE;
                        if (entering) {
                           p.displayProgress = 1.0F - (float)Math.sqrt(Math.max(0.0, 1.0 - curE));
                        } else {
                           p.displayProgress = (float)Math.sqrt(Math.max(0.0, curE));
                        }

                        p.hadEntering = entering;
                     }

                     float dDisp = (float)(speed / (stageSecondsF * 20.0));
                     if (entering) {
                        p.displayProgress = Math.min(1.0F, p.displayProgress + dDisp);
                     } else {
                        p.displayProgress = Math.max(0.0F, p.displayProgress - dDisp);
                     }

                     float e = entering ? 1.0F - (1.0F - p.displayProgress) * (1.0F - p.displayProgress) : p.displayProgress * p.displayProgress;
                     p.lastVisualE = e;
                     px = MathHelper.lerp(e, BOOK_X, ox);
                     pz = MathHelper.lerp(e, BOOK_Z, oz);
                     py = MathHelper.lerp(e, BOOK_Y, oy) + arcBump(e) * EMERGE_ARC;
                     scaleMul = 0.3F + 0.7F * e;
                  }
                  // 边出来边旋转（与物品一致）：转速与位置曲线同频；圈数由青金石独立配置
                  // lapisEmergeSpinTurns / lapisEmergeOutSpinTurns 控制（Mod Menu 可调）。
                  double frameSec = Math.max(0.0, dt) / 20.0;
                  double pieceStageSec = p.returning ? cfg.emergeOutSeconds : cfg.emergeSeconds;
                  double pieceTurns = p.returning ? cfg.lapisEmergeOutSpinTurns : cfg.lapisEmergeSpinTurns;
                  double pieceEntryVel = Math.max(0.0, pieceTurns) * 360.0 / Math.max(0.1, pieceStageSec);
                  double pieceBaseVel = 360.0 / cfg.lapisRotationSeconds;
                  double lapisX = p.displayProgress >= 0.0F ? MathHelper.clamp(p.displayProgress, 0.0F, 1.0F) : p.progress;
                  if (p.progress < 1.0F) {
                     double pieceMinVel = Math.max(pieceBaseVel / Math.max(pieceEntryVel, 1.0), 0.35);
                     double posVel = !p.returning
                           ? Math.max(pieceMinVel, 2.0 * (1.0 - lapisX))
                           : 2.0 * lapisX;
                     p.spinDegPerSec = pieceEntryVel * posVel;
                  } else if (p.spinDegPerSec > pieceBaseVel) {
                     // 线性摩擦减速：较缓，均匀滑停，回收更从容。
                     p.spinDegPerSec = Math.max(pieceBaseVel, p.spinDegPerSec - 100.0 * frameSec);
                  }
                  p.spinSelfDeg += p.spinDegPerSec * frameSec;

                  float yawDeg = (float)(orbitAngleDeg + p.spinSelfDeg);
                  renderItem(matrices, queue, st.lastLapis, px, py, pz, (float)(cfg.lapisScale * scaleMul), yawDeg, light, client, cameraEntity);
                  pieceIdx++;
               }
            }
         }

         if (bookOpen && cfg.animEnabled) {
            spawnBurstParticles(world, tablePos, cfg);
         }
      } finally {
         matrices.pop();
      }
   }

   private static void schedulePieces(EnchantingPreviewRenderer.TableAnimState st, int target, double now, double dt, double baseDelayTicks, PreviewConfig cfg) {
      double staggerTicks = cfg.emergeStaggerSeconds * 20.0;
      int presentCount = 0;

      for (EnchantingPreviewRenderer.LapisPiece p : st.pieces) {
         if (!p.returning) {
            presentCount++;
         }
      }

      if (target > presentCount) {
         for (EnchantingPreviewRenderer.LapisPiece p : st.pieces) {
            if (p.returning && presentCount < target) {
               p.returning = false;
               p.startAtTicks = now;
               presentCount++;
            }
         }
      }

      int activeCount = 0;

      for (EnchantingPreviewRenderer.LapisPiece p : st.pieces) {
         if (!p.returning || p.progress > 0.0F) {
            activeCount++;
         }
      }

      boolean anyReturning = false;

      for (EnchantingPreviewRenderer.LapisPiece p : st.pieces) {
         if (p.returning && p.progress > 0.0F) {
            anyReturning = true;
            break;
         }
      }

      if (!anyReturning && activeCount < target) {
         for (int i = 0; i < target - activeCount; i++) {
            EnchantingPreviewRenderer.LapisPiece piece = new EnchantingPreviewRenderer.LapisPiece();
            piece.returning = false;
            piece.progress = 0.0F;
            piece.startAtTicks = now + baseDelayTicks + i * staggerTicks;
            piece.orbitOffsetDeg = i * (360.0 / Math.max(1, target));
            st.pieces.add(piece);
         }
      } else if (activeCount > target) {
         Iterator<EnchantingPreviewRenderer.LapisPiece> it = st.pieces.iterator();

         while (it.hasNext()) {
            EnchantingPreviewRenderer.LapisPiece p = it.next();
            if (!p.returning && p.progress <= 0.0F && now < p.startAtTicks) {
               it.remove();
            }
         }

         List<EnchantingPreviewRenderer.LapisPiece> stillActive = new ArrayList<>();

         for (EnchantingPreviewRenderer.LapisPiece p : st.pieces) {
            if (!p.returning) {
               stillActive.add(p);
            }
         }

         int excess = stillActive.size() - target;

         for (int i = 0; i < excess; i++) {
            EnchantingPreviewRenderer.LapisPiece p = stillActive.get(stillActive.size() - 1 - i);
            p.returning = true;
            p.startAtTicks = now;
         }
      }

      Iterator<EnchantingPreviewRenderer.LapisPiece> it = st.pieces.iterator();

      while (it.hasNext()) {
         EnchantingPreviewRenderer.LapisPiece p = it.next();
         if (!(now < p.startAtTicks)) {
            if (!cfg.animEnabled) {
               if (p.returning) {
                  it.remove();
               } else {
                  p.progress = 1.0F;
               }
            } else {
               // 退场存活时钟与视觉时钟同速（都用 emergeOutSeconds），避免「视觉先到终点而
               // 存活仍在倒计时」导致的终点停留：emergeOut 越短，视觉越快到位，若存活仍用
               // max(emergeOut,itemEmergeOut) 慢速倒计时，停留时间 =max-emergeOut 反而越长。
               double lapisStageSeconds = p.returning ? cfg.emergeOutSeconds : cfg.emergeSeconds;
               float step = (float)(dt / (lapisStageSeconds * 20.0));
               if (p.returning) {
                  p.progress -= step;
                  if (p.progress <= 0.0F) {
                     it.remove();
                  }
               } else {
                  p.progress = Math.min(1.0F, p.progress + step);
               }
            }
         }
      }
   }

   private static void spawnBurstParticles(ClientWorld world, BlockPos pos, PreviewConfig cfg) {
      if (cfg.enchantFxStyle != PreviewConfig.EnchantFxStyle.OFF) {
         Long start;
         synchronized (BURSTS) {
            start = BURSTS.get(pos);
         }

         if (start != null) {
            double now = Util.getMeasuringTimeMs();
            double durMs = cfg.enchantFxDuration * 1000.0;
            double age = (now - start.longValue()) / durMs;
            if (!(age < 0.0) && !(age > 1.0)) {
               double p = 1.0 - Math.pow(1.0 - age, 3.0);
               double radius = 0.15 + p * (cfg.enchantFxRadius - 0.15);
               double cy = pos.getY() + 0.75 + cfg.itemHeight * 0.55 + cfg.enchantFxHeight;
               double cx = pos.getX() + 0.5;
               double cz = pos.getZ() + 0.5;
               int color = cfg.enchantFxColor & 16777215;
               double d = MathHelper.clamp(cfg.enchantFxDensity, 0, 100) / 100.0;
               int count = 1 + (int)Math.round(d * 23.0);
               float size = (float)(0.25 + d * 0.75);

               for (int i = 0; i < count; i++) {
                  double th = (Math.PI * 2) * i / count;
                  double px = cx + Math.cos(th) * radius;
                  double pz = cz + Math.sin(th) * radius;
                  world.addParticleClient(new DustParticleEffect(color, size), px, cy, pz, 0.0, 0.0, 0.0);
               }
            } else {
               synchronized (BURSTS) {
                  BURSTS.remove(pos);
               }
            }
         }
      }
   }

   private static boolean sameBaseItemIgnoringEnchantments(ItemStack a, ItemStack b) {
      if (a != null && !a.isEmpty()) {
         if (b != null && !b.isEmpty()) {
            ItemStack a1 = a.copyWithCount(1);
            ItemStack b1 = b.copyWithCount(1);
            a1.remove(DataComponentTypes.ENCHANTMENTS);
            b1.remove(DataComponentTypes.ENCHANTMENTS);
            return ItemStack.areItemsAndComponentsEqual(a1, b1);
         } else {
            return false;
         }
      } else {
         return b == null || b.isEmpty();
      }
   }

   private static boolean sameEnchantments(ItemStack a, ItemStack b) {
      boolean ae = a != null && a.contains(DataComponentTypes.ENCHANTMENTS);
      boolean be = b != null && b.contains(DataComponentTypes.ENCHANTMENTS);
      if (ae != be) {
         return false;
      } else {
         return !ae ? true : ((ItemEnchantmentsComponent)a.get(DataComponentTypes.ENCHANTMENTS)).equals(b.get(DataComponentTypes.ENCHANTMENTS));
      }
   }

   private static boolean samePreviewItem(ItemStack a, ItemStack b) {
      if (a != null && !a.isEmpty()) {
         if (b != null && !b.isEmpty()) {
            ItemStack a1 = a.copyWithCount(1);
            ItemStack b1 = b.copyWithCount(1);
            a1.remove(DataComponentTypes.ENCHANTMENTS);
            b1.remove(DataComponentTypes.ENCHANTMENTS);
            return ItemStack.areItemsAndComponentsEqual(a1, b1);
         } else {
            return false;
         }
      } else {
         return b == null || b.isEmpty();
      }
   }

   private static double arcBump(double e) {
      double s = Math.sin(e * Math.PI);
      return s * s;
   }

   private static double radPerTick(double secondsPerCycle) {
      return (Math.PI * 2) / (secondsPerCycle * 20.0);
   }

   private static void translateToBlock(MatrixStack matrices, Camera camera, BlockPos pos) {
      Vec3d cam = camera.getCameraPos();
      matrices.translate(pos.getX() - cam.getX(), pos.getY() - cam.getY(), pos.getZ() - cam.getZ());
   }

   /** 光照缓存：每个位置缓存 20 tick（1s），昼夜/方块变化最多滞后 1s 刷新，省去每帧 chunk 查询。 */
   private static final Map<BlockPos, long[]> LIGHT_CACHE = new HashMap<>();

   private static int computeLight(ClientWorld world, PreviewConfig cfg, BlockPos tablePos) {
      if (cfg.lightingMode == PreviewConfig.LightingMode.FULL) {
         return 15728880;
      }

      long tick = world.getTime();
      long[] cached = LIGHT_CACHE.get(tablePos);
      if (cached != null && cached[0] + 20 >= tick) {
         return (int) cached[1];
      }
      BlockPos samplePos = tablePos.up();
      int blockLight = world.getLightLevel(LightType.BLOCK, samplePos);
      int skyLight = world.getLightLevel(LightType.SKY, samplePos);
      int packed = LightmapTextureManager.pack(blockLight, skyLight);
      LIGHT_CACHE.put(tablePos, new long[] { tick, packed });
      return packed;
   }

   private static void renderItem(
      MatrixStack matrices,
      OrderedRenderCommandQueue queue,
      ItemStack stack,
      double x,
      double y,
      double z,
      float scale,
      double yawAngle,
      int light,
      MinecraftClient client,
      Entity cameraEntity
   ) {
      ItemRenderState itemState = new ItemRenderState();
      client.getItemModelManager().updateForNonLivingEntity(itemState, stack, ItemDisplayContext.FIXED, cameraEntity);
      if (!itemState.isEmpty()) {
         matrices.push();

         try {
            matrices.translate(x, y, z);
            if (yawAngle != 0.0) {
               matrices.multiply(RotationAxis.POSITIVE_Y.rotation((float)(yawAngle * (float) (Math.PI / 180.0))));
            }

            matrices.scale(scale, scale, scale);
            itemState.render(matrices, queue, light, OverlayTexture.DEFAULT_UV, 0);
         } finally {
            matrices.pop();
         }
      }
   }

   private static final class LapisPiece {
      float progress;
      boolean returning;
      double startAtTicks = -1.0;
      double orbitOffsetDeg;
      double spinAccum;
      double spinDecay;
      double spinDegPerSec = 90.0;
      double spinSelfDeg;
      float displayProgress = -1.0F;
      boolean hadEntering = true;
      float lastVisualE;
   }

   private static final class TableAnimState {
      double lastFrameTicks = -1.0;
      double lastSmoothTick = -1.0;
      double lastWallFrameTicks = -1.0;
      boolean wasOpen;
      float itemProgress;
      double itemStartAt = -1.0;
      double itemSpinAccum;
      double spinDegPerSec = 90.0;
      double returnExtraDeg;
      double spinDecay;
      float displayProgress = -1.0F;
      boolean hadEntering = true;
      float lastVisualE;
      boolean hadItemVisible;
      boolean wasItemVisible;
      ItemStack lastItem = ItemStack.EMPTY;
      boolean needReplay;
      ItemStack lastLapis = ItemStack.EMPTY;
      final List<EnchantingPreviewRenderer.LapisPiece> pieces = new ArrayList<>();
      int lastLapisTarget;
      int lapisZeroHoldFrames;
   }
}