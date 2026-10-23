<template>
  <div class="space-y-5">
    <header class="space-y-2">
      <h1 class="text-2xl font-semibold tracking-tight">叙事工坊</h1>
      <p class="max-w-2xl text-sm text-muted-foreground">
        挑一场聊过的对话，改写成小说、剧本或大纲。生成后可以改稿、导出，也能发布到广场。
      </p>
    </header>

    <Tabs v-model="tab" class="gap-0">
      <TabsList class="w-full sm:w-fit">
        <TabsTrigger value="generate">生成作品</TabsTrigger>
        <TabsTrigger value="mine">我的作品</TabsTrigger>
        <TabsTrigger value="published">社区广场</TabsTrigger>
      </TabsList>

      <!-- 生成 -->
      <TabsContent value="generate" class="mt-4 space-y-4">
        <!-- 配置区 -->
        <Card class="gap-0 py-4">
          <CardContent class="grid gap-4 px-4 sm:px-6 lg:grid-cols-[minmax(0,1fr)_320px] lg:gap-6">
            <div class="space-y-4">
              <div class="space-y-2">
                <div class="flex items-center justify-between gap-2">
                  <Label class="text-xs font-medium text-muted-foreground">对话素材</Label>
                  <span v-if="selectedSession" class="text-[11px] text-muted-foreground">
                    {{ selectedSession.messageCount || 0 }} 条消息
                  </span>
                </div>

                <DropdownMenu>
                  <DropdownMenuTrigger as-child>
                    <Button
                      variant="outline"
                      class="h-10 w-full justify-between px-3 font-normal"
                      :disabled="generating"
                    >
                      <span class="flex min-w-0 items-center gap-2">
                        <MessageSquare class="size-4 shrink-0 text-muted-foreground" />
                        <span class="truncate">
                          {{ selectedSession ? selectedSession.title || '未命名会话' : '选择一场对话' }}
                        </span>
                      </span>
                      <ChevronDown class="size-4 shrink-0 text-muted-foreground" />
                    </Button>
                  </DropdownMenuTrigger>

                  <DropdownMenuContent class="w-[min(92vw,420px)]">
                    <DropdownMenuLabel class="text-xs font-normal text-muted-foreground">
                      选择对话素材
                    </DropdownMenuLabel>
                    <DropdownMenuSeparator />

                    <div
                      v-if="loadingSessions"
                      class="px-2 py-6 text-center text-xs text-muted-foreground"
                    >
                      正在加载会话…
                    </div>
                    <div
                      v-else-if="!sessions.length"
                      class="px-2 py-6 text-center text-xs text-muted-foreground"
                    >
                      还没有可改写的对话，至少 4 条消息才能生成。
                    </div>
                    <template v-else>
                      <DropdownMenuItem
                        v-for="s in sessions"
                        :key="s.id"
                        class="cursor-pointer items-start gap-3 py-2"
                        :title="sessionLabel(s)"
                        @select="pickSession(s)"
                      >
                        <div class="min-w-0 flex-1">
                          <p class="truncate text-sm">{{ s.title || '未命名会话' }}</p>
                          <p class="mt-0.5 truncate text-xs text-muted-foreground">
                            {{ s.characterName || '未知角色' }} · {{ s.messageCount || 0 }} 条
                          </p>
                        </div>
                        <Check
                          v-if="String(s.id) === String(form.sessionId)"
                          class="mt-1 size-4 shrink-0"
                        />
                      </DropdownMenuItem>
                    </template>
                  </DropdownMenuContent>
                </DropdownMenu>

                <p v-if="!loadingSessions && !sessions.length" class="text-xs text-muted-foreground">
                  还没有足够的对话。先去和角色聊几句，至少 4 条消息才能改写。
                </p>
              </div>

              <div class="grid gap-4 sm:grid-cols-2">
                <div class="space-y-2">
                  <Label class="text-xs font-medium text-muted-foreground">作品形式</Label>
                  <div class="flex rounded-lg border border-border/60 bg-muted/40 p-0.5">
                    <Button
                      v-for="opt in formTypes"
                      :key="opt.value"
                      :variant="form.formType === opt.value ? 'secondary' : 'ghost'"
                      size="xs"
                      class="h-7 flex-1 rounded-md px-2 text-xs font-normal"
                      :class="
                        form.formType === opt.value
                          ? 'text-foreground'
                          : 'text-muted-foreground hover:text-foreground'
                      "
                      :disabled="generating"
                      @click="form.formType = opt.value"
                    >
                      {{ opt.label }}
                    </Button>
                  </div>
                </div>

                <div class="space-y-2">
                  <Label class="text-xs font-medium text-muted-foreground">篇幅</Label>
                  <div class="flex rounded-lg border border-border/60 bg-muted/40 p-0.5">
                    <Button
                      v-for="opt in lengthOptions"
                      :key="opt.value"
                      :variant="form.length === opt.value ? 'secondary' : 'ghost'"
                      size="xs"
                      class="h-7 flex-1 rounded-md px-2 text-xs font-normal"
                      :class="
                        form.length === opt.value
                          ? 'text-foreground'
                          : 'text-muted-foreground hover:text-foreground'
                      "
                      :disabled="generating"
                      @click="form.length = opt.value"
                    >
                      {{ opt.label }}
                    </Button>
                  </div>
                </div>
              </div>

              <div class="space-y-2">
                <Label class="text-xs font-medium text-muted-foreground">文风</Label>
                <div
                  class="grid grid-cols-2 gap-0.5 rounded-lg border border-border/60 bg-muted/40 p-0.5 sm:grid-cols-4"
                >
                  <Button
                    v-for="opt in styleOptions"
                    :key="opt.value"
                    :variant="form.style === opt.value ? 'secondary' : 'ghost'"
                    size="xs"
                    class="h-7 w-full rounded-md px-2 text-xs font-normal"
                    :class="
                      form.style === opt.value
                        ? 'text-foreground'
                        : 'text-muted-foreground hover:text-foreground'
                    "
                    :disabled="generating"
                    @click="form.style = opt.value"
                  >
                    {{ opt.label }}
                  </Button>
                </div>
              </div>
            </div>

            <div class="flex flex-col gap-3">
              <div class="space-y-2">
                <div class="flex items-baseline justify-between gap-2">
                  <Label for="extra-instruction" class="text-xs font-medium text-muted-foreground">
                    额外要求
                  </Label>
                  <span class="text-[11px] text-muted-foreground">可选</span>
                </div>
                <Textarea
                  id="extra-instruction"
                  v-model="form.extraInstruction"
                  :rows="3"
                  maxlength="200"
                  :disabled="generating"
                  placeholder="例如：结局写成开放式；突出雨夜场景"
                  class="min-h-[84px] resize-none"
                />
                <div class="flex items-center justify-between text-[11px] text-muted-foreground">
                  <span>会影响语气与结尾</span>
                  <span>{{ form.extraInstruction.length }}/200</span>
                </div>
              </div>

              <div class="mt-auto flex flex-col gap-2">
                <Button class="w-full" :disabled="!canGenerate" @click="startGenerate">
                  <Loader2 v-if="generating" class="size-4 animate-spin" />
                  <Sparkles v-else class="size-4" />
                  {{ generating ? '正在改写…' : '开始生成' }}
                </Button>
                <Button v-if="generating" variant="secondary" class="w-full" @click="abortGenerate">
                  <Square class="size-3.5" />
                  停止生成
                </Button>
                <p class="text-center text-[11px] text-muted-foreground">生成完成后自动存为草稿</p>
              </div>
            </div>
          </CardContent>
        </Card>

        <!-- 结果区 -->
        <Card class="flex min-h-[560px] flex-col gap-0 py-0">
          <CardHeader class="border-b border-border/60 px-4 py-3 sm:px-6">
            <div class="flex min-w-0 items-center gap-2">
              <CardTitle class="text-sm font-medium">改写结果</CardTitle>
              <Badge v-if="meta.messageCount" variant="secondary" class="font-normal">
                {{ meta.messageCount }} 条素材
              </Badge>
              <Badge v-if="content" variant="outline" class="font-normal">
                {{ content.length }} 字
              </Badge>
            </div>
            <CardDescription class="text-xs">
              {{ generating ? '正在接收生成内容…' : '按 Markdown 渲染，可编辑后再保存。' }}
            </CardDescription>
            <CardAction class="flex items-center gap-1.5">
              <Button
                v-if="generating"
                variant="secondary"
                size="xs"
                class="rounded-md"
                @click="abortGenerate"
              >
                <Square class="size-3" />
                停止
              </Button>
              <Button
                v-else-if="content"
                variant="ghost"
                size="xs"
                class="text-muted-foreground"
                @click="toggleEdit"
              >
                <Pencil class="size-3" />
                {{ editingResult ? '预览' : '编辑' }}
              </Button>
            </CardAction>
          </CardHeader>

          <CardContent class="flex flex-1 flex-col p-0">
            <div
              v-if="!content && !generating"
              class="flex flex-1 flex-col items-center justify-center gap-3 px-6 py-16 text-center"
            >
              <span class="grid size-12 place-items-center rounded-2xl bg-secondary">
                <MessageSquare v-if="!form.sessionId" class="size-5 text-muted-foreground" />
                <Sparkles v-else class="size-5 text-muted-foreground" />
              </span>
              <div class="space-y-1">
                <p class="text-sm font-medium">
                  {{
                    loadingSessions
                      ? '正在加载会话…'
                      : !sessions.length
                        ? '还没有可改写的对话'
                        : !form.sessionId
                          ? '先选一场对话'
                          : '准备就绪'
                  }}
                </p>
                <p class="mx-auto max-w-sm text-xs leading-relaxed text-muted-foreground">
                  <template v-if="loadingSessions">正在读取你的对话记录。</template>
                  <template v-else-if="!sessions.length">
                    先去和角色聊几句，至少 4 条消息才能开始改写。
                  </template>
                  <template v-else-if="!form.sessionId">
                    从上方选择对话素材，再设置形式、文风与篇幅。
                  </template>
                  <template v-else>
                    点「开始生成」，内容会逐步出现；生成后可编辑、导出或发布。
                  </template>
                </p>
              </div>
              <Button
                v-if="!loadingSessions && !sessions.length"
                variant="outline"
                size="sm"
                @click="router.push('/')"
              >
                去挑角色
              </Button>
            </div>

            <div
              v-else-if="!editingResult"
              ref="resultScrollRef"
              class="max-h-[56vh] min-h-[380px] flex-1 overflow-y-auto px-4 py-5 sm:px-6"
            >
              <div
                class="chat-md text-sm leading-7 break-words text-foreground"
                v-html="renderMarkdown(content)"
              />
              <span v-if="generating" class="nr-caret ml-0.5 inline-block text-foreground">▍</span>
              <div v-if="generating" class="mt-3 flex items-center gap-2 text-xs text-muted-foreground">
                <Loader2 class="size-3 animate-spin" />
                正在生成
              </div>
            </div>

            <div v-else class="flex flex-1 flex-col gap-2 p-4 sm:p-6">
              <Textarea
                v-model="content"
                placeholder="生成结果可以直接修改"
                class="h-[42vh] min-h-[320px] w-full resize-none leading-7"
              />
              <p class="text-right text-[11px] text-muted-foreground">{{ content.length }} 字</p>
            </div>
          </CardContent>

          <div
            class="mt-auto flex flex-wrap items-center gap-1.5 border-t border-border/60 px-4 py-3 sm:px-6"
          >
            <Button size="sm" :disabled="!savedWorkId || generating || saving" @click="saveDraft">
              <Loader2 v-if="saving" class="size-3.5 animate-spin" />
              <Save v-else class="size-3.5" />
              保存
            </Button>
            <Button
              variant="outline"
              size="sm"
              :disabled="!savedWorkId || generating"
              @click="download('txt')"
            >
              <Download class="size-3.5" />
              导出 TXT
            </Button>
            <Button
              variant="outline"
              size="sm"
              :disabled="!savedWorkId || generating"
              @click="download('md')"
            >
              <Download class="size-3.5" />
              导出 MD
            </Button>
            <Button
              variant="secondary"
              size="sm"
              :disabled="!savedWorkId || generating"
              @click="publishGenerated"
            >
              <Upload class="size-3.5" />
              发布
            </Button>
            <span v-if="savedWorkId" class="ml-1 text-[11px] text-muted-foreground">已存为草稿</span>

            <div class="flex-1" />

            <Button
              variant="ghost"
              size="sm"
              class="text-muted-foreground"
              :disabled="!content || generating"
              @click="resetResult"
            >
              清空
            </Button>
            <Button
              variant="ghost"
              size="sm"
              class="text-muted-foreground"
              @click="tab = 'mine'"
            >
              我的作品
            </Button>
          </div>
        </Card>
      </TabsContent>

      <!-- 我的作品 -->
      <TabsContent value="mine" class="mt-4 space-y-4">
        <div class="flex items-center justify-between gap-3">
          <p class="text-sm text-muted-foreground">共 {{ works.length }} 篇</p>
          <Button
            variant="ghost"
            size="sm"
            class="text-muted-foreground"
            :disabled="loadingWorks"
            @click="loadWorks"
          >
            <RefreshCw class="size-3.5" :class="{ 'animate-spin': loadingWorks }" />
            刷新
          </Button>
        </div>

        <div v-if="loadingWorks && !works.length" class="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
          <Card v-for="i in 3" :key="i" class="gap-3 py-4">
            <CardHeader class="px-4">
              <Skeleton class="h-5 w-2/3" />
            </CardHeader>
            <CardContent class="space-y-2 px-4">
              <Skeleton class="h-3 w-full" />
              <Skeleton class="h-3 w-5/6" />
              <Skeleton class="h-3 w-1/2" />
            </CardContent>
          </Card>
        </div>

        <div v-else-if="works.length" class="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
          <Card
            v-for="w in works"
            :key="w.id"
            class="gap-3 py-4 transition-colors hover:border-foreground/20"
          >
            <CardHeader class="px-4">
              <CardTitle class="line-clamp-2 text-base leading-snug">
                {{ w.title || '未命名作品' }}
              </CardTitle>
              <CardAction>
                <Badge :variant="statusBadgeVariant(w)" class="font-normal">
                  {{ statusLabel(w) }}
                </Badge>
              </CardAction>
            </CardHeader>

            <CardContent class="px-4">
              <p class="line-clamp-3 min-h-[3.75rem] text-sm leading-relaxed text-muted-foreground">
                {{ w.summary || '暂无摘要' }}
              </p>
              <div class="mt-3 flex flex-wrap items-center gap-x-3 gap-y-1 text-[11px] text-muted-foreground">
                <span>{{ formLabel(w.formType) }}</span>
                <span>{{ w.wordCount || 0 }} 字</span>
                <span>{{ w.sourceMessageCount || 0 }} 条素材</span>
                <span>{{ formatTime(w.updatedAt) }}</span>
              </div>
            </CardContent>

            <CardFooter class="flex flex-wrap gap-1.5 px-4">
              <Button variant="outline" size="xs" @click="openWork(w)">查看</Button>
              <Button variant="ghost" size="xs" class="text-muted-foreground" @click="download('txt', w)">
                TXT
              </Button>
              <Button variant="ghost" size="xs" class="text-muted-foreground" @click="download('md', w)">
                MD
              </Button>
              <Button v-if="w.status !== 1" variant="secondary" size="xs" @click="publish(w)">
                发布
              </Button>
              <Button v-else variant="ghost" size="xs" class="text-muted-foreground" @click="unpublish(w)">
                撤回
              </Button>
              <Button
                variant="ghost"
                size="xs"
                class="ml-auto text-muted-foreground hover:text-destructive"
                @click="removeWork(w)"
              >
                <Trash2 class="size-3" />
                删除
              </Button>
            </CardFooter>
          </Card>
        </div>

        <div
          v-else
          class="flex flex-col items-center justify-center gap-3 rounded-xl border border-dashed border-border/70 px-6 py-20 text-center"
        >
          <span class="grid size-12 place-items-center rounded-2xl bg-secondary">
            <BookOpen class="size-5 text-muted-foreground" />
          </span>
          <div class="space-y-1">
            <p class="text-sm font-medium">还没有作品</p>
            <p class="text-xs text-muted-foreground">去生成一篇，或挑一场旧对话改写。</p>
          </div>
          <Button variant="outline" size="sm" @click="tab = 'generate'">去生成</Button>
        </div>
      </TabsContent>

      <!-- 社区广场 -->
      <TabsContent value="published" class="mt-4 space-y-4">
        <div class="flex items-center justify-between gap-3">
          <p class="text-sm text-muted-foreground">已发布 {{ published.length }} 篇</p>
          <Button
            variant="ghost"
            size="sm"
            class="text-muted-foreground"
            :disabled="loadingPublished"
            @click="loadPublished"
          >
            <RefreshCw class="size-3.5" :class="{ 'animate-spin': loadingPublished }" />
            刷新
          </Button>
        </div>

        <div
          v-if="loadingPublished && !published.length"
          class="grid gap-4 sm:grid-cols-2 xl:grid-cols-3"
        >
          <Card v-for="i in 3" :key="i" class="gap-3 py-4">
            <CardHeader class="px-4">
              <Skeleton class="h-5 w-2/3" />
            </CardHeader>
            <CardContent class="space-y-2 px-4">
              <Skeleton class="h-3 w-full" />
              <Skeleton class="h-3 w-5/6" />
              <Skeleton class="h-3 w-1/2" />
            </CardContent>
          </Card>
        </div>

        <div v-else-if="published.length" class="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
          <Card
            v-for="w in published"
            :key="w.id"
            role="button"
            tabindex="0"
            class="cursor-pointer gap-3 py-4 transition-colors hover:border-foreground/20"
            @click="openWork(w)"
            @keydown.enter="openWork(w)"
          >
            <CardHeader class="px-4">
              <CardTitle class="line-clamp-2 text-base leading-snug">
                {{ w.title || '未命名作品' }}
              </CardTitle>
              <CardAction>
                <Avatar class="size-7">
                  <AvatarImage v-if="w.avatar" :src="w.avatar" :alt="w.nickname || '作者'" />
                  <AvatarFallback class="text-[10px]">{{ (w.nickname || '?')[0] }}</AvatarFallback>
                </Avatar>
              </CardAction>
            </CardHeader>

            <CardContent class="px-4">
              <p class="line-clamp-3 min-h-[3.75rem] text-sm leading-relaxed text-muted-foreground">
                {{ w.summary || '暂无摘要' }}
              </p>
              <div class="mt-3 flex flex-wrap items-center gap-x-3 gap-y-1 text-[11px] text-muted-foreground">
                <span>{{ w.nickname || '匿名旅人' }}</span>
                <span v-if="w.characterName">与 {{ w.characterName }}</span>
                <span>{{ w.wordCount || 0 }} 字</span>
              </div>
            </CardContent>
          </Card>
        </div>

        <div
          v-else
          class="flex flex-col items-center justify-center gap-3 rounded-xl border border-dashed border-border/70 px-6 py-20 text-center"
        >
          <span class="grid size-12 place-items-center rounded-2xl bg-secondary">
            <Globe class="size-5 text-muted-foreground" />
          </span>
          <div class="space-y-1">
            <p class="text-sm font-medium">广场还没有公开的作品</p>
            <p class="text-xs text-muted-foreground">生成一篇并发布，就会出现在这里。</p>
          </div>
        </div>
      </TabsContent>
    </Tabs>

    <!-- 阅读 / 编辑 -->
    <Dialog v-model:open="readerOpen">
      <DialogContent class="flex max-h-[88vh] flex-col gap-4 sm:max-w-3xl">
        <DialogHeader>
          <DialogTitle class="pr-6 leading-snug">{{ current?.title || '作品' }}</DialogTitle>
          <DialogDescription class="text-xs">
            在这里修改正文，保存后会同步到「我的作品」。
          </DialogDescription>
        </DialogHeader>

        <div class="flex flex-wrap items-center gap-x-3 gap-y-2 text-xs text-muted-foreground">
          <Badge variant="secondary" class="font-normal">{{ formLabel(current?.formType) }}</Badge>
          <span>{{ current?.wordCount || 0 }} 字</span>
          <span>{{ current?.sourceMessageCount || 0 }} 条素材</span>
          <span v-if="current?.sessionId">来源会话 #{{ current.sessionId }}</span>
        </div>

        <Textarea
          v-model="readerContent"
          placeholder="正文"
          class="h-[46vh] w-full resize-none leading-7"
        />

        <DialogFooter>
          <Button variant="ghost" @click="readerOpen = false">关闭</Button>
          <Button variant="outline" @click="download('txt', current)">
            <Download class="size-4" />
            导出 TXT
          </Button>
          <Button :disabled="saving" @click="saveEdit">
            <Loader2 v-if="saving" class="size-4 animate-spin" />
            保存修改
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>

    <!-- 删除确认 -->
    <Dialog v-model:open="deleteOpen">
      <DialogContent class="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>删除作品</DialogTitle>
          <DialogDescription>
            确定删除《{{ deleteTarget?.title || '未命名作品' }}》？删除后不可恢复。
          </DialogDescription>
        </DialogHeader>
        <DialogFooter>
          <Button variant="outline" :disabled="deleting" @click="deleteOpen = false">取消</Button>
          <Button variant="destructive" :disabled="deleting" @click="confirmRemoveWork">
            <Loader2 v-if="deleting" class="size-4 animate-spin" />
            删除
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { toast } from 'vue-sonner'
import {
  BookOpen,
  Check,
  ChevronDown,
  Download,
  Globe,
  Loader2,
  MessageSquare,
  Pencil,
  RefreshCw,
  Save,
  Sparkles,
  Square,
  Trash2,
  Upload,
} from 'lucide-vue-next'
import {
  deleteNarrativeWork,
  fetchNarrativeExport,
  getNarrativeSessions,
  getNarrativeWorks,
  getPublishedWorks,
  publishNarrativeWork,
  streamNarrative,
  unpublishNarrativeWork,
  updateNarrativeWork,
} from '../api/narrative'
import { useAuthStore } from '../stores/auth'
import { renderMarkdown } from '../utils/markdown'
import { Avatar, AvatarFallback, AvatarImage } from '@/components/ui/avatar'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import {
  Card,
  CardAction,
  CardContent,
  CardDescription,
  CardFooter,
  CardHeader,
  CardTitle,
} from '@/components/ui/card'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import { Label } from '@/components/ui/label'
import { Skeleton } from '@/components/ui/skeleton'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { Textarea } from '@/components/ui/textarea'

const router = useRouter()
const authStore = useAuthStore()

const tab = ref('generate')
const sessions = ref([])
const works = ref([])
const published = ref([])
const loadingSessions = ref(false)
const loadingWorks = ref(false)
const loadingPublished = ref(false)
const generating = ref(false)
const saving = ref(false)
const content = ref('')
const meta = reactive({ messageCount: 0, dialogueChars: 0 })

const form = reactive({
  sessionId: null,
  formType: 'novel',
  style: 'literary',
  length: 'medium',
  extraInstruction: '',
})

const formTypes = [
  { value: 'novel', label: '小说' },
  { value: 'script', label: '剧本' },
  { value: 'outline', label: '大纲' },
]

const styleOptions = [
  { value: 'literary', label: '文学叙事' },
  { value: 'colloquial', label: '口语自然' },
  { value: 'script', label: '戏剧对白' },
  { value: 'poetic', label: '偏诗意' },
]

const lengthOptions = [
  { value: 'short', label: '短' },
  { value: 'medium', label: '中' },
  { value: 'long', label: '长' },
]

let controller = null
const savedWorkId = ref(null)

const canGenerate = computed(() => !!form.sessionId && !generating.value)

const selectedSession = computed(
  () => sessions.value.find((s) => String(s.id) === String(form.sessionId)) || null,
)

const readerOpen = ref(false)
const current = ref(null)
const readerContent = ref('')

const deleteOpen = ref(false)
const deleteTarget = ref(null)
const deleting = ref(false)

const editingResult = ref(false)
const resultScrollRef = ref(null)

function sessionLabel(s) {
  const who = s.characterName ? s.characterName : '未知角色'
  return `${s.title || '未命名会话'} · ${who}（${s.messageCount || 0} 条）`
}

function formLabel(t) {
  return { novel: '小说', script: '剧本', outline: '大纲' }[t] || '小说'
}

function statusLabel(w) {
  if (w.status === 1) return '已发布'
  if (w.auditStatus === 2) return '发布未通过'
  return '草稿'
}

function statusTagType(w) {
  if (w.status === 1) return 'success'
  if (w.auditStatus === 2) return 'danger'
  return 'info'
}

function statusBadgeVariant(w) {
  return { success: 'default', danger: 'destructive', info: 'secondary' }[statusTagType(w)] || 'secondary'
}

function formatTime(t) {
  if (!t) return ''
  return String(t).replace('T', ' ').slice(0, 16)
}

function pickSession(s) {
  form.sessionId = s.id
  editingResult.value = false
}

function toggleEdit() {
  if (generating.value) return
  editingResult.value = !editingResult.value
}

async function loadSessions() {
  loadingSessions.value = true
  try {
    sessions.value = (await getNarrativeSessions()) || []
  } catch (e) {
    toast.error(e.message || '会话列表加载失败')
  } finally {
    loadingSessions.value = false
  }
}

async function startGenerate() {
  if (!canGenerate.value) return
  generating.value = true
  editingResult.value = false
  content.value = ''
  savedWorkId.value = null
  meta.messageCount = 0
  meta.dialogueChars = 0
  controller = new AbortController()

  try {
    await streamNarrative(
      { ...form },
      {
        signal: controller.signal,
        onMeta: (m) => {
          meta.messageCount = m.messageCount || 0
          meta.dialogueChars = m.dialogueChars || 0
        },
        onChunk: (chunk) => {
          content.value += chunk
        },
        onDone: async (id) => {
          savedWorkId.value = id
          toast.success('已保存为作品草稿')
          await Promise.all([loadWorks(), authStore.fetchUser()])
        },
        onError: (err) => {
          if (err?.name === 'AbortError') return
          toast.error(err.message || '生成失败')
        },
      },
    )
  } catch (err) {
    if (err?.name !== 'AbortError') {
      toast.error(err.message || '生成失败')
    }
  } finally {
    generating.value = false
    controller = null
  }
}

function abortGenerate() {
  controller?.abort()
  generating.value = false
  toast.success('已停止')
}

function resetResult() {
  content.value = ''
  savedWorkId.value = null
  editingResult.value = false
  meta.messageCount = 0
  meta.dialogueChars = 0
}

async function loadWorks() {
  loadingWorks.value = true
  try {
    works.value = (await getNarrativeWorks()) || []
  } catch (e) {
    toast.error(e.message || '作品列表加载失败')
  } finally {
    loadingWorks.value = false
  }
}

async function loadPublished() {
  loadingPublished.value = true
  try {
    published.value = (await getPublishedWorks()) || []
  } catch (e) {
    toast.error(e.message || '广场加载失败')
  } finally {
    loadingPublished.value = false
  }
}

function openWork(w) {
  current.value = w
  readerContent.value = w.content || ''
  readerOpen.value = true
}

async function saveEdit() {
  if (!current.value) return
  saving.value = true
  try {
    const data = await updateNarrativeWork(current.value.id, {
      title: current.value.title,
      content: readerContent.value,
    })
    current.value = data
    toast.success('已保存')
    await loadWorks()
  } catch (e) {
    toast.error(e.message || '保存失败')
  } finally {
    saving.value = false
  }
}

/** 保存生成区当前正文（草稿由服务端在生成完成时创建） */
async function saveDraft() {
  if (!savedWorkId.value) {
    toast.warning('还没有可保存的草稿')
    return
  }
  if (!content.value.trim()) {
    toast.warning('内容为空，无法保存')
    return
  }
  const source =
    works.value.find((w) => w.id === savedWorkId.value) ||
    (current.value?.id === savedWorkId.value ? current.value : null)
  if (!source) {
    toast.warning('草稿尚未同步，请到「我的作品」里保存')
    return
  }

  saving.value = true
  try {
    await updateNarrativeWork(savedWorkId.value, {
      title: source.title,
      content: content.value,
    })
    editingResult.value = false
    toast.success('已保存')
    await loadWorks()
  } catch (e) {
    toast.error(e.message || '保存失败')
  } finally {
    saving.value = false
  }
}

function publishGenerated() {
  if (!savedWorkId.value) {
    toast.warning('还没有可发布的作品')
    return
  }
  publish({ id: savedWorkId.value })
}

async function publish(w) {
  try {
    await publishNarrativeWork(w.id)
    toast.success('已发布到广场')
    await Promise.all([loadWorks(), loadPublished()])
  } catch (e) {
    toast.error(e.message || '发布失败')
    await loadWorks()
  }
}

async function unpublish(w) {
  try {
    await unpublishNarrativeWork(w.id)
    toast.success('已撤回')
    await Promise.all([loadWorks(), loadPublished()])
  } catch (e) {
    toast.error(e.message || '撤回失败')
  }
}

function removeWork(w) {
  deleteTarget.value = w
  deleteOpen.value = true
}

async function confirmRemoveWork() {
  const w = deleteTarget.value
  if (!w) return
  deleting.value = true
  try {
    await deleteNarrativeWork(w.id)
    toast.success('已删除')
    deleteOpen.value = false
    deleteTarget.value = null
    await loadWorks()
  } catch (e) {
    toast.error(e.message || '删除失败')
  } finally {
    deleting.value = false
  }
}

async function download(format, w) {
  const id = w?.id ?? savedWorkId.value
  if (!id) {
    toast.warning('还没有可导出的作品')
    return
  }
  try {
    const response = await fetchNarrativeExport(id, format)
    const blob = await response.blob()
    const a = document.createElement('a')
    a.href = URL.createObjectURL(blob)
    a.download = `${(w?.title || 'narrative').replace(/[\\/:*?"<>|]/g, '_')}.${format}`
    a.click()
    URL.revokeObjectURL(a.href)
  } catch (e) {
    toast.error(e.message || '导出失败')
  }
}

// 流式输出时保持结果区贴底；用户切换页签或编辑时不动
watch(content, async () => {
  if (!generating.value || editingResult.value) return
  await nextTick()
  const el = resultScrollRef.value
  if (el) el.scrollTop = el.scrollHeight
})

onMounted(async () => {
  await Promise.all([loadSessions(), loadWorks(), loadPublished()])
})

onBeforeUnmount(() => {
  controller?.abort()
})
</script>

<style scoped>
.nr-caret {
  animation: nr-blink 1s step-end infinite;
}

@keyframes nr-blink {
  50% {
    opacity: 0;
  }
}
</style>
