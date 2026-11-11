<template>
  <div class="flex h-[calc(100dvh-3.5rem)] w-full">
    <!-- 会话侧栏 -->
    <aside class="hidden w-64 shrink-0 flex-col border-r border-border/60 md:flex">
      <div class="flex h-14 items-center justify-between px-4">
        <h2 class="text-sm font-medium">历史会话</h2>
        <span class="text-xs text-muted-foreground">{{ sessions.length }}</span>
      </div>

      <div class="min-h-0 flex-1 overflow-y-auto px-2 pb-3">
        <SessionList :sessions="sessions" :current-id="sessionId" @select="openSession" />
      </div>

      <div class="border-t border-border/60 p-2">
        <Button variant="outline" size="sm" class="w-full" @click="router.push('/')">
          <Plus class="size-4" />
          开始新对话
        </Button>
      </div>
    </aside>

    <!-- 主对话区 -->
    <section class="flex min-w-0 flex-1 flex-col">
      <!-- 会话头 -->
      <header class="flex h-14 shrink-0 items-center gap-3 border-b border-border/60 px-4">
        <Button
          variant="ghost"
          size="icon-sm"
          class="md:hidden"
          title="历史会话"
          @click="mobileSessionsOpen = true"
        >
          <PanelLeft class="size-4" />
        </Button>

        <!--
          桌面端左侧会话列表已经显示了这个角色的头像和名字，这里再摆一遍就是重复；
          只在侧边栏收起的移动端显示头像与标题。
        -->
        <Avatar class="size-8 shrink-0 md:hidden">
          <AvatarImage :src="bgUrl" :alt="characterName" />
          <AvatarFallback class="text-xs">{{ characterName[0] }}</AvatarFallback>
        </Avatar>

        <div class="min-w-0 flex-1">
          <p class="truncate text-sm font-medium md:hidden">{{ characterName }}</p>
          <p class="truncate text-xs text-muted-foreground">
            积分 <span class="text-foreground">{{ formatNum(pointsBalance) }}</span>
            · 约 {{ tokensPerPoint }} Token / 积分
          </p>
        </div>

        <Button
          v-if="characterId"
          variant="ghost"
          size="icon-sm"
          :title="favorited ? '取消收藏' : '收藏角色'"
          @click="toggleFav"
        >
          <Heart class="size-4" :class="favorited ? 'fill-foreground text-foreground' : ''" />
        </Button>

        <Button variant="ghost" size="sm" class="hidden sm:inline-flex" @click="rechargeOpen = true">
          <Coins class="size-4" />
          充值
        </Button>

        <!-- 人设 / 记忆入口：有内容时高亮，方便一眼看到「你是谁、记着什么」 -->
        <Button
          v-if="sessionId"
          variant="ghost"
          size="icon-sm"
          :title="hasPersonaOrMemory ? '我与记忆（已生效）' : '我与记忆'"
          @click="personaMemoryOpen = true"
        >
          <UserRound
            class="size-4"
            :class="hasPersonaOrMemory ? 'text-foreground' : 'opacity-70'"
          />
        </Button>

        <!-- 作者注释生效时给个显式标记，否则用户会忘记自己设过 -->
        <Button
          v-if="sessionId && hasAuthorNote"
          variant="ghost"
          size="icon-sm"
          title="作者注释生效中"
          @click="authorNoteOpen = true"
        >
          <StickyNote class="size-4" />
        </Button>

        <!-- 多选删除入口：进入后每条消息前出现勾选框 -->
        <Button
          v-if="sessionId && messages.length && !selecting"
          variant="ghost"
          size="icon-sm"
          title="选择消息删除"
          @click="enterSelecting"
        >
          <ListChecks class="size-4" />
        </Button>

        <DropdownMenu v-if="sessionId">
          <DropdownMenuTrigger as-child>
            <Button variant="ghost" size="icon-sm">
              <MoreHorizontal class="size-4" />
            </Button>
          </DropdownMenuTrigger>
          <DropdownMenuContent align="end">
            <DropdownMenuItem class="cursor-pointer" @select="personaMemoryOpen = true">
              <UserRound class="size-4 opacity-70" />
              我与记忆
            </DropdownMenuItem>
            <DropdownMenuItem class="cursor-pointer" @select="authorNoteOpen = true">
              <StickyNote class="size-4 opacity-70" />
              作者注释
            </DropdownMenuItem>
            <DropdownMenuItem class="cursor-pointer" @select="removeSession">
              <Trash2 class="size-4 opacity-70" />
              删除这段会话
            </DropdownMenuItem>
          </DropdownMenuContent>
        </DropdownMenu>
      </header>

      <!-- 消息区 -->
      <div
        ref="listRef"
        class="min-h-0 flex-1 overflow-y-auto"
        @scroll="onScroll"
      >
        <div v-if="!messages.length && !streaming" class="flex h-full flex-col items-center justify-center px-6 text-center">
          <span class="grid size-12 place-items-center rounded-2xl bg-secondary">
            <Sparkles class="size-5 text-muted-foreground" />
          </span>
          <p class="mt-4 text-lg font-medium">
            {{ sessionId ? `向「${characterName}」说第一句话吧` : '开始一段新故事' }}
          </p>
          <p class="mt-1.5 max-w-sm text-sm text-muted-foreground">
            {{
              sessionId
                ? hasPersonaOrMemory
                  ? '人设与记忆已就绪。开聊后早期剧情会自动压成摘要，角色不容易失忆。'
                  : '建议先点右上角「我与记忆」写上你是谁——角色会按这个身份对你说话。'
                : '从角色广场挑一个角色进入对话，或从左侧打开历史会话。'
            }}
          </p>
          <div v-if="sessionId && !hasPersonaOrMemory" class="mt-5">
            <Button variant="outline" @click="personaMemoryOpen = true">
              <UserRound class="size-4" />
              设置我的人设
            </Button>
          </div>
          <Button v-else-if="!sessionId" variant="outline" class="mt-5" @click="router.push('/')">
            去角色广场
          </Button>
        </div>

        <div v-else class="mx-auto w-full max-w-3xl space-y-6 px-4 py-6">
          <div
            v-for="m in messages"
            :key="m.id || m._tmp"
            class="group flex gap-3"
            :class="[
              m.role === 'user' ? 'flex-row-reverse' : '',
              m._streaming ? '' : m.role === 'user' ? 'msg-in-user' : 'msg-in-ai',
            ]"
          >
            <!-- 多选模式下的勾选框 -->
            <button
              v-if="selecting && m.id"
              type="button"
              class="mt-1 grid size-5 shrink-0 place-items-center rounded-full border transition-colors"
              :class="
                isSelected(m)
                  ? 'border-foreground bg-foreground text-background'
                  : 'border-border text-transparent hover:border-foreground/50'
              "
              :title="isSelected(m) ? '取消选择' : '选择这条'"
              @click.stop="toggleSelect(m)"
            >
              <Check class="size-3" />
            </button>

            <!-- 头像：助手用角色头像，用户用自己 -->
            <Avatar class="mt-0.5 size-7 shrink-0">
              <template v-if="m.role === 'user'">
                <AvatarImage :src="authStore.user?.avatar" :alt="userName" />
                <AvatarFallback class="text-[10px]">{{ userName[0] }}</AvatarFallback>
              </template>
              <template v-else>
                <AvatarImage :src="bgUrl" :alt="characterName" />
                <AvatarFallback class="text-[10px]">{{ characterName[0] }}</AvatarFallback>
              </template>
            </Avatar>

            <div class="flex min-w-0 max-w-[85%] flex-col" :class="m.role === 'user' ? 'items-end' : 'items-start'">
              <div
                class="rounded-2xl px-4 py-2.5 text-sm leading-relaxed break-words"
                :class="[
                  m.role === 'user'
                    ? 'whitespace-pre-wrap rounded-tr-md bg-secondary text-secondary-foreground'
                    : 'rounded-tl-md border border-border/60 bg-card',
                  selecting && isSelected(m) ? 'ring-2 ring-foreground/40' : '',
                  selecting ? 'cursor-pointer' : '',
                ]"
                @click="selecting && toggleSelect(m)"
              >
                <span v-if="m.role === 'user'">{{ m.content }}</span>
                <template v-else>
                  <!-- 思考链：默认折叠，流式时若已有内容自动展开 -->
                  <details
                    v-if="messageReasoning(m)"
                    class="mb-2 rounded-lg border border-border/50 bg-secondary/40 px-2.5 py-1.5 text-xs text-muted-foreground"
                    :open="m._streaming && !!messageReasoning(m)"
                  >
                    <summary class="cursor-pointer select-none text-[11px] font-medium tracking-wide text-muted-foreground/90">
                      思考过程
                    </summary>
                    <pre class="mt-1.5 max-h-48 overflow-y-auto whitespace-pre-wrap font-sans text-[12px] leading-relaxed text-muted-foreground">{{ messageReasoning(m) }}</pre>
                  </details>
                  <!-- 模型输出带 markdown，解析后渲染；原始 HTML 已在 renderMarkdown 中丢弃 -->
                  <div class="chat-md" v-html="renderMarkdown(m.content)" />
                </template>
                <span v-if="m._streaming" class="stream-caret">▍</span>
              </div>

              <!-- 悬停时出现的消息操作 -->
              <div
                class="mt-1 flex flex-wrap items-center gap-1 opacity-0 transition-opacity group-hover:opacity-100"
                :class="m.role === 'user' ? 'flex-row-reverse' : ''"
              >
                <Button variant="ghost" size="icon-xs" title="复制" @click="copyMessage(m)">
                  <Copy class="size-3" />
                </Button>

                <!-- 多版本：左右切，右箭头到头了就用「再生成一版」 -->
                <template v-if="m.role === 'assistant' && m.id && !streaming">
                  <Button
                    variant="ghost"
                    size="icon-xs"
                    title="上一版"
                    :disabled="(m.swipeIndex || 0) <= 0"
                    @click="switchSwipe(m, (m.swipeIndex || 0) - 1)"
                  >
                    <ChevronLeft class="size-3" />
                  </Button>
                  <span
                    class="min-w-7 text-center text-[10px] tabular-nums text-muted-foreground"
                    :title="`共 ${m.swipeCount || 1} 版`"
                  >
                    {{ (m.swipeIndex || 0) + 1 }}/{{ m.swipeCount || 1 }}
                  </span>
                  <Button
                    variant="ghost"
                    size="icon-xs"
                    title="下一版"
                    :disabled="(m.swipeIndex || 0) >= (m.swipeCount || 1) - 1"
                    @click="switchSwipe(m, (m.swipeIndex || 0) + 1)"
                  >
                    <ChevronRight class="size-3" />
                  </Button>
                  <Button variant="ghost" size="icon-xs" title="再生成一版" @click="newSwipe(m)">
                    <RotateCcw class="size-3" />
                  </Button>
                </template>

                <Button
                  v-if="m.id && !streaming"
                  variant="ghost"
                  size="icon-xs"
                  title="编辑"
                  @click="openEdit(m)"
                >
                  <Pencil class="size-3" />
                </Button>
                <Button
                  v-if="m.id && !streaming"
                  variant="ghost"
                  size="icon-xs"
                  title="从这里分支"
                  @click="branch(m)"
                >
                  <GitBranch class="size-3" />
                </Button>
                <Button
                  v-if="m.id && !streaming && !selecting"
                  variant="ghost"
                  size="xs"
                  class="h-7 gap-1 px-2 text-destructive hover:text-destructive"
                  title="删除本轮：用户消息 + 助手回复，并从模型上下文移除"
                  @click="askDeleteRound(m)"
                >
                  <Trash2 class="size-3" />
                  删本轮
                </Button>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- 输入区 -->
      <div class="shrink-0 border-t border-border/60 bg-background/60 px-4 py-3 backdrop-blur">
        <div class="mx-auto w-full max-w-3xl">
          <!-- 多选删除的浮动操作条 -->
          <div
            v-if="selecting"
            class="mb-2 flex items-center justify-between gap-2 rounded-xl border border-border/80 bg-secondary px-3 py-2"
          >
            <span class="text-xs text-muted-foreground">
              已选 <span class="font-medium text-foreground">{{ selectedIds.length }}</span> 条
            </span>
            <div class="flex gap-1.5">
              <Button variant="ghost" size="xs" @click="exitSelecting">取消</Button>
              <Button
                variant="destructive"
                size="xs"
                :disabled="!selectedIds.length || deleting"
                @click="askDeleteSelected"
              >
                <Trash2 class="size-3.5" />
                删除
              </Button>
            </div>
          </div>

          <!-- 上下文占用条：把后端的窗口裁剪显式画出来 -->
          <div v-if="ctxMeta" class="mb-2">
            <div class="flex h-1 w-full overflow-hidden rounded-full bg-muted">
              <div class="bg-foreground/80" :style="{ width: `${ctxSysPct}%` }" title="角色人设提示" />
              <div class="bg-foreground/45" :style="{ width: `${ctxDiaPct}%` }" title="历史对话" />
              <div class="bg-foreground/15" :style="{ width: `${ctxOutPct}%` }" title="输出预留" />
            </div>
            <p class="mt-1 text-[10px] text-muted-foreground">
              上下文 {{ ctxMeta.usedRounds }}/{{ ctxMeta.window }} 轮
              <span v-if="ctxMeta.droppedRounds > 0" class="text-foreground/70">
                · 已丢弃 {{ ctxMeta.droppedRounds }} 条更早的消息
              </span>
              <span v-if="ctxMeta.hasMemory" class="text-foreground/70"> · 已注入剧情摘要</span>
              <span v-if="ctxMeta.hasPersona" class="text-foreground/70"> · 人设生效</span>
              <span class="opacity-70"> · 约 {{ ctxMeta.estimatedTokens }} tokens 输入</span>
            </p>
            <!-- 本轮世界书命中：不抢戏，只轻轻标一下 -->
            <div
              v-if="activatedEntries.length"
              class="mt-1.5 flex flex-wrap items-center gap-1.5"
              title="本轮提示词里插入了这些世界书条目"
            >
              <BookMarked class="size-3 shrink-0 text-muted-foreground" />
              <span class="text-[10px] text-muted-foreground">本轮命中</span>
              <span
                v-for="title in activatedEntries"
                :key="title"
                class="rounded-md bg-secondary px-1.5 py-0.5 text-[10px] text-foreground/80"
              >{{ title }}</span>
            </div>
          </div>

          <div class="rounded-2xl border border-border/80 bg-card p-2 transition-shadow focus-within:border-ring/60 focus-within:shadow-[0_0_0_4px_rgba(228,196,137,0.10)]">
            <textarea
              ref="inputRef"
              v-model="input"
              rows="1"
              placeholder="说点什么… Enter 发送，Shift + Enter 换行"
              class="max-h-40 w-full resize-none bg-transparent px-2 py-1.5 text-sm outline-none placeholder:text-muted-foreground"
              @input="autoResize"
              @keydown.enter.exact.prevent="send"
            />
            <div class="flex items-center justify-between gap-2 px-1 pt-1">
              <div class="flex min-w-0 flex-wrap items-center gap-1.5">
                <!-- 模型选择：放输入区更显眼，按厂商分组，列表可滚动 -->
                <DropdownMenu>
                  <DropdownMenuTrigger as-child>
                    <Button
                      variant="outline"
                      size="xs"
                      class="h-7 max-w-[14rem] shrink-0 gap-1 px-2"
                      :disabled="modelSaving || streaming"
                      title="切换对话模型（Key 在服务端，这里只选模型名）"
                    >
                      <Cpu class="size-3.5 shrink-0 opacity-70" />
                      <span class="truncate">{{ currentModelLabel }}</span>
                      <ChevronsUpDown class="size-3 shrink-0 opacity-50" />
                    </Button>
                  </DropdownMenuTrigger>
                  <DropdownMenuContent align="start" class="w-72 p-1">
                    <DropdownMenuLabel class="text-xs text-muted-foreground">
                      选择模型 · {{ chatModels.length }} 个
                    </DropdownMenuLabel>
                    <div class="max-h-72 overflow-y-auto py-1">
                      <template v-for="(group, gi) in modelGroups" :key="group.key">
                        <DropdownMenuSeparator v-if="gi > 0" />
                        <DropdownMenuLabel class="text-[10px] tracking-wide text-muted-foreground uppercase">
                          {{ group.label }}
                        </DropdownMenuLabel>
                        <DropdownMenuRadioGroup
                          :model-value="selectedModel"
                          @update:model-value="onModelChange"
                        >
                          <DropdownMenuRadioItem
                            v-for="m in group.models"
                            :key="m.id"
                            :value="m.id"
                            class="cursor-pointer items-start py-2"
                          >
                            <div class="flex min-w-0 flex-col gap-0.5">
                              <span class="truncate">{{ m.label }}</span>
                              <span class="truncate font-mono text-[10px] font-normal text-muted-foreground">
                                {{ m.id }}
                              </span>
                            </div>
                          </DropdownMenuRadioItem>
                        </DropdownMenuRadioGroup>
                      </template>
                    </div>
                  </DropdownMenuContent>
                </DropdownMenu>

                <Button
                  v-if="supportsThinking"
                  variant="ghost"
                  size="xs"
                  class="h-7 shrink-0 gap-1 px-2"
                  :class="thinkingEnabled ? 'text-foreground' : 'text-muted-foreground'"
                  :title="thinkingEnabled ? '思考模式开：会展示推理过程，稍慢' : '思考模式关：更快更稳（推荐扮演）'"
                  :disabled="streaming"
                  @click="toggleThinking"
                >
                  <Brain class="size-3.5" />
                  {{ thinkingEnabled ? '思考开' : '思考关' }}
                </Button>
                <span class="truncate text-[11px] text-muted-foreground">
                  {{ streaming ? '对方正在回应…' : ctxMeta ? `上下文约 ${ctxMeta.estimatedTokens} tokens` : '选模型后开聊' }}
                </span>
                <Button
                  v-if="lastRoundAnchor && !streaming && !selecting"
                  variant="ghost"
                  size="xs"
                  class="h-7 shrink-0 gap-1 px-2 text-muted-foreground hover:text-destructive"
                  title="删除最近一轮用户提问与助手回复，并从模型上下文移除"
                  @click="askDeleteRound(lastRoundAnchor)"
                >
                  <Undo2 class="size-3.5" />
                  撤销上一轮
                </Button>
              </div>
              <Button
                v-if="streaming"
                variant="secondary"
                size="sm"
                class="rounded-full"
                @click="stopGenerating"
              >
                <Square class="size-3.5" />
                停止
              </Button>
              <Button
                v-else
                size="icon-sm"
                class="rounded-full transition-transform hover:scale-105 active:scale-95"
                :disabled="!input.trim()"
                title="发送"
                @click="send"
              >
                <ArrowUp class="size-4" />
              </Button>
            </div>
          </div>
        </div>
      </div>
    </section>

    <!-- 移动端会话抽屉 -->
    <Sheet v-model:open="mobileSessionsOpen">
      <SheetContent side="left" class="w-72 p-0">
        <SheetHeader class="border-b border-border/60 px-4 py-3">
          <SheetTitle class="text-sm">历史会话</SheetTitle>
          <SheetDescription class="sr-only">选择会话</SheetDescription>
        </SheetHeader>
        <div class="flex h-[calc(100dvh-3.5rem)] flex-col">
          <div class="min-h-0 flex-1 overflow-y-auto px-2 py-2">
            <SessionList :sessions="sessions" :current-id="sessionId" @select="openSessionFromDrawer" />
          </div>
          <div class="border-t border-border/60 p-2">
            <Button variant="outline" size="sm" class="w-full" @click="router.push('/')">
              <Plus class="size-4" />
              开始新对话
            </Button>
          </div>
        </div>
      </SheetContent>
    </Sheet>

    <MockRechargeDialog v-model="rechargeOpen" prefer="basic" @success="onRecharged" />

    <AuthorNotePanel v-model:open="authorNoteOpen" :session-id="sessionId" @saved="refreshSessions" />
    <PersonaMemoryPanel
      v-model:open="personaMemoryOpen"
      :session-id="sessionId"
      @saved="onPersonaMemorySaved"
    />

    <!-- 编辑消息：改文本不重新生成，也不额外扣积分 -->
    <Dialog v-model:open="editOpen">
      <DialogContent class="max-w-lg">
        <DialogHeader>
          <DialogTitle>编辑消息</DialogTitle>
          <DialogDescription>
            保存后立即生效，不会重新问模型，也不额外扣积分。AI 那边不会自动知道这次修改。
          </DialogDescription>
        </DialogHeader>
        <Textarea
          v-model="editText"
          :rows="8"
          class="mt-2 resize-y text-[13px] leading-relaxed"
          placeholder="改写这段内容…"
        />
        <DialogFooter class="mt-4 flex-row justify-end gap-2">
          <Button variant="outline" @click="editOpen = false">取消</Button>
          <Button :disabled="editSaving || !editText.trim()" @click="submitEdit">
            <Loader2 v-if="editSaving" class="size-4 animate-spin" />
            <Check v-else class="size-4" />
            保存
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>

    <!-- 删除选中消息的确认框 -->
    <Dialog v-model:open="deleteOpen">
      <DialogContent class="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>删除 {{ selectedIds.length }} 条消息？</DialogTitle>
          <DialogDescription>
            删除后这些内容不会再进入上下文，也就是说 AI 会「忘掉」它们。
            操作不可恢复，已扣的积分不退。
          </DialogDescription>
        </DialogHeader>
        <DialogFooter class="flex-row justify-end gap-2">
          <Button variant="outline" :disabled="deleting" @click="deleteOpen = false">取消</Button>
          <Button variant="destructive" :disabled="deleting" @click="confirmDeleteSelected">
            <Loader2 v-if="deleting" class="size-4 animate-spin" />
            <Trash2 v-else class="size-4" />
            删除
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>

    <!-- 删除本轮确认 -->
    <Dialog v-model:open="roundDeleteOpen">
      <DialogContent class="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>删除这一轮对话？</DialogTitle>
          <DialogDescription>
            会删掉用户消息和对应的助手回复（共约 {{ roundPreviewCount }} 条）。
            删除后不会再进入上下文，下一轮生成时 AI 会「忘掉」它们。不可恢复。
          </DialogDescription>
        </DialogHeader>
        <DialogFooter class="flex-row justify-end gap-2">
          <Button variant="outline" :disabled="deleting" @click="roundDeleteOpen = false">取消</Button>
          <Button variant="destructive" :disabled="deleting || !roundTargetId" @click="confirmDeleteRound">
            <Loader2 v-if="deleting" class="size-4 animate-spin" />
            <Trash2 v-else class="size-4" />
            删除本轮
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { toast } from 'vue-sonner'
import {
  ArrowUp,
  BookMarked,
  Brain,
  Check,
  ChevronLeft,
  ChevronRight,
  ChevronsUpDown,
  Coins,
  Copy,
  Cpu,
  GitBranch,
  Heart,
  ListChecks,
  Loader2,
  MoreHorizontal,
  PanelLeft,
  Pencil,
  Plus,
  RotateCcw,
  Sparkles,
  Square,
  StickyNote,
  Trash2,
  Undo2,
  UserRound,
} from 'lucide-vue-next'
import {
  branchSession,
  createSession,
  deleteMessages,
  deleteRound,
  deleteSession,
  editMessage,
  getChatModels,
  getMessages,
  getSessions,
  selectSwipe,
  setSessionModel,
  streamChat,
  streamSwipe,
} from '../api/chat'
import { favoriteCharacter, getCharacterDetail } from '../api/character'
import { getTokenBalance } from '../api/user'
import { useAuthStore } from '../stores/auth'
import { renderMarkdown } from '../utils/markdown'
import MockRechargeDialog from '../components/MockRechargeDialog.vue'
import AuthorNotePanel from '../components/chat/AuthorNotePanel.vue'
import PersonaMemoryPanel from '../components/chat/PersonaMemoryPanel.vue'
import SessionList from '../components/chat/SessionList.vue'
import { Avatar, AvatarFallback, AvatarImage } from '@/components/ui/avatar'
import { Button } from '@/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Textarea } from '@/components/ui/textarea'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuRadioGroup,
  DropdownMenuRadioItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import {
  Sheet,
  SheetContent,
  SheetDescription,
  SheetHeader,
  SheetTitle,
} from '@/components/ui/sheet'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

const sessions = ref([])
const messages = ref([])
const input = ref('')
const streaming = ref(false)
/** 后端 meta 事件带来的上下文裁剪统计，用于输入框上方的占用条 */
const ctxMeta = ref(null)
const listRef = ref(null)
const inputRef = ref(null)
const favorited = ref(false)
const favLoading = ref(false)
const pointsBalance = ref(0)
const tokensPerPoint = ref(1000)
const rechargeOpen = ref(false)
const mobileSessionsOpen = ref(false)
const authorNoteOpen = ref(false)
const personaMemoryOpen = ref(false)
/** 人设 / 摘要是否已有内容（会话列表字段或本轮 meta） */
const personaMemoryHint = ref({ persona: false, memory: false })
const editOpen = ref(false)
const editTarget = ref(null)
const editText = ref('')
const editSaving = ref(false)
/** 多选删除：进入后每条消息前面出现勾选框 */
const selecting = ref(false)
const selectedIds = ref([])
const deleteOpen = ref(false)
const deleting = ref(false)
/** 删除本轮：以某条消息为锚点，连同配对的用户/助手一起删 */
const roundDeleteOpen = ref(false)
const roundTargetId = ref(null)
const roundPreviewCount = ref(2)
/** 用户是否贴在底部：贴底才自动跟随流式输出，否则不打断向上翻阅 */
const stickToBottom = ref(true)
/** 与后端 AiModelService.CHAT_MODELS 对齐；接口失败时也能切全量 */
const FALLBACK_CHAT_MODELS = [
  { id: 'deepseek-v4.1-flash', label: 'DeepSeek V4.1 Flash', description: '默认推荐' },
  { id: 'deepseek-v4-flash', label: 'DeepSeek V4 Flash', description: '上一代 Flash' },
  { id: 'deepseek-v4-pro', label: 'DeepSeek V4 Pro', description: '更强能力' },
  { id: 'deepseek-v3.2', label: 'DeepSeek V3.2', description: '经典 DeepSeek' },
  { id: 'gemini-3-flash', label: 'Gemini 3 Flash', description: 'Google' },
  { id: 'gemini-3.8-flash', label: 'Gemini 3.8 Flash', description: 'Google' },
  { id: 'gemini-4-argon', label: 'Gemini 4 Argon', description: 'Google' },
  { id: 'claude-opus-4-6', label: 'Claude Opus 4.6', description: 'Anthropic' },
  { id: 'glm-5.3', label: 'GLM 5.3', description: '智谱' },
  { id: 'glm-5.3-flash', label: 'GLM 5.3 Flash', description: '智谱' },
  { id: 'kimi-k3', label: 'Kimi K3', description: '月之暗面' },
]
const chatModels = ref([...FALLBACK_CHAT_MODELS])
const defaultModel = ref('deepseek-v4.1-flash')
const selectedModel = ref(localStorage.getItem('tavern-chat-model') || 'deepseek-v4.1-flash')
const modelSaving = ref(false)

const MODEL_GROUP_DEFS = [
  { key: 'deepseek', label: 'DeepSeek', match: (id) => id.startsWith('deepseek') },
  { key: 'gemini', label: 'Gemini', match: (id) => id.startsWith('gemini') },
  { key: 'claude', label: 'Claude', match: (id) => id.startsWith('claude') },
  { key: 'glm', label: '智谱 GLM', match: (id) => id.startsWith('glm') },
  { key: 'kimi', label: 'Kimi', match: (id) => id.startsWith('kimi') },
]

const modelGroups = computed(() => {
  const list = chatModels.value || []
  const groups = MODEL_GROUP_DEFS.map((g) => ({
    key: g.key,
    label: g.label,
    models: list.filter((m) => g.match(String(m.id || ''))),
  })).filter((g) => g.models.length > 0)
  const used = new Set(groups.flatMap((g) => g.models.map((m) => m.id)))
  const other = list.filter((m) => !used.has(m.id))
  if (other.length) {
    groups.push({ key: 'other', label: '其它', models: other })
  }
  return groups
})
/** DeepSeek 思考模式：默认关（扮演更稳）；开了会多一段可折叠「思考过程」 */
const thinkingEnabled = ref(localStorage.getItem('tavern-thinking') === '1')
let suppressLoad = false
let abortCtrl = null

const sessionId = computed(() => route.params.sessionId)
const current = computed(() => sessions.value.find((s) => isCurrent(s.id)))

const currentModelLabel = computed(() => {
  const hit = chatModels.value.find((m) => m.id === selectedModel.value)
  return hit?.label || selectedModel.value || '选择模型'
})

/** 思考开关只对 DeepSeek 有意义；其它模型网关可能直接 400 */
const supportsThinking = computed(() =>
  String(selectedModel.value || '').toLowerCase().startsWith('deepseek'),
)

const activatedEntries = computed(() => {
  const list = ctxMeta.value?.activatedEntries
  return Array.isArray(list) ? list.filter((t) => t != null && String(t).trim() !== '') : []
})

function messageReasoning(m) {
  return (m?.reasoning || m?._reasoning || '').trim()
}

function toggleThinking() {
  if (!supportsThinking.value) return
  thinkingEnabled.value = !thinkingEnabled.value
  localStorage.setItem('tavern-thinking', thinkingEnabled.value ? '1' : '0')
  toast.message(thinkingEnabled.value ? '已开启思考模式' : '已关闭思考模式')
}

const characterId = computed(() => {
  const id = current.value?.characterId ?? current.value?.character_id ?? route.query.characterId
  return id != null && id !== '' ? Number(id) : null
})

const characterName = computed(
  () => current.value?.characterName || current.value?.title || '酒馆',
)

/** 气泡上的称呼：会话人设名优先，和提示词里的 {{user}} 一致 */
const userName = computed(() => {
  const persona = current.value?.personaName ?? current.value?.persona_name
  if (persona && String(persona).trim()) return String(persona).trim()
  return authStore.user?.nickname || authStore.user?.username || '我'
})

/** 会话列表里已经带了 author_note / an_frequency，直接用，不额外请求 */
const hasAuthorNote = computed(() => {
  const s = current.value
  if (!s) return false
  const note = s.authorNote ?? s.author_note
  const freq = s.anFrequency ?? s.an_frequency
  return !!note && String(note).trim() !== '' && (freq == null || Number(freq) > 0)
})

const hasPersonaOrMemory = computed(() => {
  const s = current.value
  if (personaMemoryHint.value.persona || personaMemoryHint.value.memory) return true
  if (!s) return false
  const name = s.personaName ?? s.persona_name
  const desc = s.personaDesc ?? s.persona_desc
  const mem = s.memorySummary ?? s.memory_summary
  const memOn = s.memoryEnabled ?? s.memory_enabled
  const personaOn = !!(name && String(name).trim()) || !!(desc && String(desc).trim())
  const memoryOn =
    !!(mem && String(mem).trim()) && (memOn == null || Number(memOn) === 1)
  return personaOn || memoryOn
})

function syncPersonaMemoryHintFromSession(session) {
  if (!session) {
    personaMemoryHint.value = { persona: false, memory: false }
    return
  }
  const name = session.personaName ?? session.persona_name
  const desc = session.personaDesc ?? session.persona_desc
  const mem = session.memorySummary ?? session.memory_summary
  const memOn = session.memoryEnabled ?? session.memory_enabled
  personaMemoryHint.value = {
    persona: !!(name && String(name).trim()) || !!(desc && String(desc).trim()),
    memory: !!(mem && String(mem).trim()) && (memOn == null || Number(memOn) === 1),
  }
}

function onPersonaMemorySaved() {
  refreshSessions()
}

const bgUrl = computed(() => {
  const url = current.value?.characterAvatar || current.value?.character_avatar || ''
  return typeof url === 'string' && /^https?:\/\//i.test(url) ? url : ''
})

/**
 * 占用条三段宽度。分母是「输入估算 + 输出上限」，即模型这次能吃下的总量。
 * 超出部分会触发裁剪，所以这个比例就是上下文压力的直接体现。
 */
const ctxBudget = computed(() => {
  const m = ctxMeta.value
  if (!m) return 1
  return (m.estimatedTokens || 0) + (m.maxTokens || 500) || 1
})
const ctxSysPct = computed(() =>
  ctxMeta.value ? Math.min(Math.round(((ctxMeta.value.systemChars || 0) / 1.5 / ctxBudget.value) * 100), 100) : 0,
)
const ctxDiaPct = computed(() =>
  ctxMeta.value
    ? Math.min(
        Math.round((((ctxMeta.value.dialogueChars || 0) / 1.5) / ctxBudget.value) * 100),
        100 - ctxSysPct.value,
      )
    : 0,
)
const ctxOutPct = computed(() =>
  ctxMeta.value
    ? Math.min(
        Math.round(((ctxMeta.value.maxTokens || 0) / ctxBudget.value) * 100),
        100 - ctxSysPct.value - ctxDiaPct.value,
      )
    : 0,
)

function isCurrent(id) {
  return String(id) === String(sessionId.value)
}

function formatNum(n) {
  return Number(n || 0).toLocaleString()
}

async function refreshTokenBalance() {
  try {
    const b = await getTokenBalance()
    pointsBalance.value = Number(b.points || 0)
    tokensPerPoint.value = Number(b.tokensPerPoint || 1000)
    if (authStore.user) {
      authStore.user.points = pointsBalance.value
      authStore.user.tokenBalance = b.tokenBalance
    }
  } catch {
    /* 余额拉取失败不阻塞对话 */
  }
}

async function onRecharged(res) {
  if (res?.pointsBalance != null) pointsBalance.value = Number(res.pointsBalance)
  else if (res?.balance != null) pointsBalance.value = Number(res.balance)
  else await refreshTokenBalance()
  await authStore.fetchUser()
}

async function refreshSessions() {
  sessions.value = (await getSessions()) || []
  const cur = sessions.value.find((s) => isCurrent(s.id))
  if (cur) {
    syncModelFromSession(cur)
    syncPersonaMemoryHintFromSession(cur)
  }
}

async function loadFavoriteState() {
  if (!characterId.value) {
    favorited.value = false
    return
  }
  try {
    const c = await getCharacterDetail(characterId.value)
    favorited.value = !!c?.favorited
  } catch {
    favorited.value = false
  }
}

async function toggleFav() {
  if (!characterId.value || favLoading.value) return
  favLoading.value = true
  try {
    const res = await favoriteCharacter(characterId.value)
    favorited.value = !!res?.favorited
    toast.success(favorited.value ? '已收藏' : '已取消收藏')
  } catch (e) {
    toast.error(e.message || '操作失败')
  } finally {
    favLoading.value = false
  }
}

async function loadMessages() {
  if (!sessionId.value) {
    if (!streaming.value) messages.value = []
    return
  }
  if (streaming.value || suppressLoad) return
  try {
    const list = await getMessages(sessionId.value)
    if (streaming.value || suppressLoad) return
    messages.value = Array.isArray(list) ? list : []
    stickToBottom.value = true
    await scrollBottom()
  } catch (e) {
    console.warn('loadMessages failed', e)
  }
}

function openSession(id) {
  if (streaming.value) {
    toast.warning('请等待当前回复完成')
    return
  }
  router.push(`/chat/${id}`)
}

function openSessionFromDrawer(id) {
  mobileSessionsOpen.value = false
  openSession(id)
}

async function loadChatModels() {
  try {
    const data = await getChatModels()
    if (Array.isArray(data?.models) && data.models.length) {
      chatModels.value = data.models
    }
    if (data?.defaultModel) {
      defaultModel.value = data.defaultModel
      if (!chatModels.value.some((m) => m.id === selectedModel.value)) {
        selectedModel.value = data.defaultModel
      }
    }
  } catch {
    /* 用本地兜底列表 */
  }
}

function syncModelFromSession(session) {
  if (!session) return
  const name = session.modelName ?? session.model_name
  if (name && chatModels.value.some((m) => m.id === name)) {
    selectedModel.value = name
    localStorage.setItem('tavern-chat-model', name)
  } else if (!name) {
    selectedModel.value = defaultModel.value
  }
}

async function onModelChange(id) {
  if (!id || id === selectedModel.value) return
  const prev = selectedModel.value
  selectedModel.value = id
  localStorage.setItem('tavern-chat-model', id)
  const label = chatModels.value.find((m) => m.id === id)?.label || id
  if (!sessionId.value) {
    toast.success(`已选择 ${label}（开聊后生效）`)
    return
  }
  modelSaving.value = true
  try {
    await setSessionModel(sessionId.value, id)
    await refreshSessions()
    toast.success(`已切换到 ${label}`)
  } catch (e) {
    selectedModel.value = prev
    localStorage.setItem('tavern-chat-model', prev)
    toast.error(e?.message || '切换模型失败')
  } finally {
    modelSaving.value = false
  }
}

async function ensureSession(cid) {
  const list = sessions.value.length ? sessions.value : ((await getSessions()) || [])
  sessions.value = list
  const existing = list.find((s) => Number(s.characterId ?? s.character_id) === Number(cid))
  if (existing) {
    syncModelFromSession(existing)
    return existing
  }
  return createSession({ characterId: Number(cid), modelName: selectedModel.value })
}

function appendByTmp(tmpId, chunk) {
  if (!chunk) return
  const idx = messages.value.findIndex((m) => m._tmp === tmpId)
  if (idx < 0) return
  const cur = messages.value[idx]
  messages.value[idx] = { ...cur, content: (cur.content || '') + chunk, _streaming: true }
  if (stickToBottom.value) scrollBottom()
}

function appendReasoningByTmp(tmpId, chunk) {
  if (!chunk) return
  const idx = messages.value.findIndex((m) => m._tmp === tmpId)
  if (idx < 0) return
  const cur = messages.value[idx]
  messages.value[idx] = {
    ...cur,
    reasoning: (cur.reasoning || '') + chunk,
    _reasoning: (cur._reasoning || cur.reasoning || '') + chunk,
    _streaming: true,
  }
  if (stickToBottom.value) scrollBottom()
}

function stopStreamingFlag(tmpId) {
  const idx = messages.value.findIndex((m) => m._tmp === tmpId)
  if (idx >= 0) {
    messages.value[idx] = { ...messages.value[idx], _streaming: false }
  }
  streaming.value = false
}

/** 用户点了「停止」：中断请求，保留已经收到的部分文字 */
function stopGenerating() {
  abortCtrl?.abort()
  const last = messages.value.findLast?.((m) => m._streaming)
  if (last) last._streaming = false
  streaming.value = false
  toast.info('已停止生成')
}

async function copyMessage(m) {
  try {
    await navigator.clipboard.writeText(m.content || '')
    toast.success('已复制')
  } catch {
    toast.error('复制失败')
  }
}

async function send() {
  if (!input.value.trim() || streaming.value) return
  if (pointsBalance.value <= 0) {
    toast.warning('积分不足，请先充值或签到')
    rechargeOpen.value = true
    return
  }

  let sid = sessionId.value
  if (!sid) {
    const cid = Number(route.query.characterId)
    if (!cid) {
      toast.warning('请先从角色详情进入对话')
      return
    }
    const s = await ensureSession(cid)
    sid = s.id
    suppressLoad = true
    await router.replace(`/chat/${sid}`)
    await refreshSessions()
    await nextTick()
    suppressLoad = false
  }

  const text = input.value.trim()
  input.value = ''
  await nextTick()
  autoResize()

  const userTmp = `u-${Date.now()}`
  const assistantTmp = `a-${Date.now()}`
  messages.value.push({ role: 'user', content: text, _tmp: userTmp })
  messages.value.push({
    role: 'assistant',
    content: '',
    reasoning: '',
    _tmp: assistantTmp,
    _streaming: true,
  })
  streaming.value = true
  stickToBottom.value = true
  abortCtrl = new AbortController()
  await scrollBottom()

  try {
    await streamChat(
      sid,
      { message: text, thinking: thinkingEnabled.value },
      {
        onChunk: ({ content, reasoning, meta }) => {
          // meta 里带上下文裁剪统计，用来画占用条 / 世界书命中
          if (meta) {
            ctxMeta.value = meta
            if (meta.hasPersona != null || meta.hasMemory != null) {
              personaMemoryHint.value = {
                persona: !!meta.hasPersona || personaMemoryHint.value.persona,
                memory: !!meta.hasMemory || personaMemoryHint.value.memory,
              }
            }
          }
          if (reasoning) appendReasoningByTmp(assistantTmp, reasoning)
          if (content) appendByTmp(assistantTmp, content)
        },
        onDone: async () => {
          stopStreamingFlag(assistantTmp)
          suppressLoad = false
          await loadMessages()
          await refreshSessions()
          await refreshTokenBalance()
        },
        onError: (e) => {
          stopStreamingFlag(assistantTmp)
          const msg = e?.message || '发送失败'
          toast.error(msg)
          if (String(msg).includes('余额不足') || String(msg).includes('积分不足') || String(msg).includes('Token')) {
            rechargeOpen.value = true
            refreshTokenBalance()
          }
        },
        signal: abortCtrl.signal,
      },
    )
  } catch (e) {
    if (e?.name === 'AbortError') streaming.value = false
  }
}

/**
 * 再生成一版：不复用、不覆盖，直接在原消息上加一个版本。
 *
 * 流式期间先把气泡清空当作占位；失败时回滚成原来那一版，别让用户丢掉已看到的内容。
 */
async function newSwipe(m) {
  if (streaming.value || !m.id) return
  if (pointsBalance.value <= 0) {
    toast.warning('积分不足，请先充值或签到')
    rechargeOpen.value = true
    return
  }

  const backup = {
    content: m.content,
    reasoning: m.reasoning || '',
    index: m.swipeIndex || 0,
  }
  streaming.value = true
  m.content = ''
  m.reasoning = ''
  m._streaming = true
  stickToBottom.value = true
  abortCtrl = new AbortController()
  await scrollBottom()

  const restore = () => {
    m.content = backup.content
    m.reasoning = backup.reasoning
    m.swipeIndex = backup.index
    m._streaming = false
    streaming.value = false
  }

  try {
    await streamSwipe(
      m.id,
      {
        onChunk: ({ content, reasoning, meta }) => {
          if (meta) ctxMeta.value = meta
          if (reasoning) {
            m.reasoning = (m.reasoning || '') + reasoning
            if (stickToBottom.value) scrollBottom()
          }
          if (content) {
            m.content += content
            if (stickToBottom.value) scrollBottom()
          }
        },
        onDone: async () => {
          m._streaming = false
          streaming.value = false
          await loadMessages()
          await refreshSessions()
          await refreshTokenBalance()
        },
        onError: (e) => {
          restore()
          toast.error(e?.message || '再生成失败')
        },
        signal: abortCtrl.signal,
      },
      { thinking: thinkingEnabled.value },
    )
  } catch (e) {
    if (e?.name === 'AbortError') {
      // 用户主动停止：保留已收到的半截，重新拉一次拿真实的版本号
      m._streaming = false
      streaming.value = false
      await loadMessages()
    } else {
      restore()
      toast.error(e?.message || '再生成失败')
    }
  }
}

/** 左右切版本：纯读库，不扣积分 */
async function switchSwipe(m, index) {
  const count = m.swipeCount || 1
  if (streaming.value || !m.id || index < 0 || index >= count || index === (m.swipeIndex || 0)) {
    return
  }
  try {
    const res = await selectSwipe(m.id, index)
    m.content = res?.content ?? m.content
    m.reasoning = res?.reasoning ?? ''
    m.swipeIndex = res?.swipeIndex ?? index
    m.swipeCount = res?.swipeCount ?? count
  } catch (e) {
    toast.error(e?.message || '切换版本失败')
  }
}

function openEdit(m) {
  editTarget.value = m
  editText.value = m.content || ''
  editOpen.value = true
}

async function submitEdit() {
  const target = editTarget.value
  const text = editText.value.trim()
  if (!target?.id || !text) return
  editSaving.value = true
  try {
    const res = await editMessage(target.id, text)
    target.content = res?.content ?? text
    editOpen.value = false
    toast.success('已保存')
  } catch (e) {
    toast.error(e?.message || '保存失败')
  } finally {
    editSaving.value = false
  }
}

/** 分支：复制到这条消息为止，原会话不动 */
async function branch(m) {
  if (streaming.value || !m.id) return
  try {
    const s = await branchSession(m.id)
    await refreshSessions()
    toast.success('已分支出一段新会话，原对话没动')
    router.push(`/chat/${s.id}`)
  } catch (e) {
    toast.error(e?.message || '分支失败')
  }
}

async function removeSession() {
  if (streaming.value) {
    toast.warning('请等待当前回复完成')
    return
  }
  await deleteSession(sessionId.value)
  await refreshSessions()
  router.push('/chat')
}

/* ---------- 多选删除：选中一段消息，从库里和上下文里一起清掉 ---------- */

function enterSelecting() {
  if (streaming.value) {
    toast.warning('请等待当前回复完成')
    return
  }
  if (!messages.value.length) {
    toast.warning('这段会话还没有消息')
    return
  }
  selecting.value = true
  selectedIds.value = []
}

function exitSelecting() {
  selecting.value = false
  selectedIds.value = []
}

function isSelected(m) {
  return m.id != null && selectedIds.value.includes(m.id)
}

function toggleSelect(m) {
  if (m.id == null) return
  const i = selectedIds.value.indexOf(m.id)
  if (i >= 0) {
    selectedIds.value = selectedIds.value.filter((id) => id !== m.id)
  } else {
    selectedIds.value = [...selectedIds.value, m.id]
  }
}

function askDeleteSelected() {
  if (!selectedIds.value.length) return
  deleteOpen.value = true
}

async function confirmDeleteSelected() {
  if (!selectedIds.value.length || deleting.value) return
  deleting.value = true
  try {
    const res = await deleteMessages(sessionId.value, selectedIds.value)
    toast.success(`已删除 ${res?.deleted ?? selectedIds.value.length} 条，之后不会再进入上下文`)
    deleteOpen.value = false
    exitSelecting()
    await loadMessages()
    await refreshSessions()
  } catch (e) {
    toast.error(e?.message || '删除失败')
  } finally {
    deleting.value = false
  }
}

/** 预览本轮会删掉哪些 id（仅前端提示；真正配对以后端为准） */
function previewRoundIds(m) {
  if (!m?.id) return []
  const list = messages.value.filter((x) => x.id != null)
  const idx = list.findIndex((x) => x.id === m.id)
  if (idx < 0) return [m.id]
  const ids = [m.id]
  if (m.role === 'user') {
    const next = list[idx + 1]
    if (next?.role === 'assistant') ids.push(next.id)
  } else if (m.role === 'assistant') {
    const prev = list[idx - 1]
    if (prev?.role === 'user') ids.unshift(prev.id)
  }
  return ids
}

/** 输入框旁「撤销上一轮」：锚定最近一条有 id 的助手消息（或用户消息） */
const lastRoundAnchor = computed(() => {
  const list = messages.value.filter((x) => x.id != null && !x._tmp)
  for (let i = list.length - 1; i >= 0; i--) {
    const m = list[i]
    if (m.role === 'assistant' || m.role === 'user') return m
  }
  return null
})

function askDeleteRound(m) {
  if (streaming.value) {
    toast.warning('请等待当前回复完成')
    return
  }
  if (!m?.id) return
  roundTargetId.value = m.id
  roundPreviewCount.value = previewRoundIds(m).length
  roundDeleteOpen.value = true
}

async function confirmDeleteRound() {
  if (!roundTargetId.value || deleting.value || !sessionId.value) return
  deleting.value = true
  try {
    const res = await deleteRound(sessionId.value, roundTargetId.value)
    const n = res?.deleted ?? res?.roundIds?.length ?? roundPreviewCount.value
    toast.success(`已删除本轮 ${n} 条，之后不会再进入上下文`)
    roundDeleteOpen.value = false
    roundTargetId.value = null
    await loadMessages()
    await refreshSessions()
  } catch (e) {
    toast.error(e?.message || '删除本轮失败')
  } finally {
    deleting.value = false
  }
}

/** 输入框随内容长高，最多 160px */
function autoResize() {
  const el = inputRef.value
  if (!el) return
  el.style.height = 'auto'
  el.style.height = `${Math.min(el.scrollHeight, 160)}px`
}

function onScroll() {
  const el = listRef.value
  if (!el) return
  stickToBottom.value = el.scrollHeight - el.scrollTop - el.clientHeight < 80
}

async function scrollBottom() {
  await nextTick()
  if (listRef.value) listRef.value.scrollTop = listRef.value.scrollHeight
}

watch(sessionId, (id, prev) => {
  if (streaming.value || suppressLoad) return
  if (String(id) === String(prev)) return
  const cur = sessions.value.find((s) => isCurrent(s.id))
  syncModelFromSession(cur)
  loadMessages()
})
watch(characterId, loadFavoriteState, { immediate: true })

onMounted(async () => {
  await loadChatModels()
  await refreshSessions()
  await refreshTokenBalance()
  if (route.query.characterId && !sessionId.value) {
    const s = await ensureSession(Number(route.query.characterId))
    suppressLoad = true
    await router.replace(`/chat/${s.id}`)
    await refreshSessions()
    suppressLoad = false
  }
  await loadMessages()
  await loadFavoriteState()
})
</script>
