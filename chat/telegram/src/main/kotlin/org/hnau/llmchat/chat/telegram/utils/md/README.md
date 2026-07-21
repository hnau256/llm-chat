Пайплайн преобразует Markdown в список строк, пригодных для отправки в Telegram
(parse_mode=HTML, лимит 4096 символов на сообщение).


Этапы пайплайна
===============

   String.mdToTGMessages()
   ├── mdToTGHtml()               → List<TGHtmlPart>
   ├── toTextBlock()              → TextBlock?
   └── chunk(maxLength) + toText() → NonEmptyList<String>?


1. mdToTGHtml() — Markdown → HTML-фрагменты
--------------------------------------------

commonmark-парсер строит AST, визитор HtmlBuilder обходит узлы и собирает
список TGHtmlPart. Каждый фрагмент имеет kind=Text или kind=Html.

                   ┌──────────────────────────────┐
  Markdown         │        HtmlBuilder            │
  ─────────        │                               │
  **bold**   ───▶  │ StrongEmphasis → tag("b")     │──▶ TGHtmlPart(Html, "<b>bold</b>")
  text      ───▶  │ Text → appendEscaped()         │──▶ TGHamlPart(Text, "text")
  ─────────        │                               │
                   └──────────────────────────────┘

Соседние фрагменты одного kind склеиваются в один.

Поддерживаемые узлы commonmark и их HTML-представление:

  Узел                  HTML
  ─────────────────────────────────────────────
  Text                  текст (с экранированием & < >)
  StrongEmphasis        <b>...</b>
  Emphasis               <i>...</i>
  Code                   <code>...</code>
  Heading (все уровни)   <b>...</b>\n
  Paragraph              ...\n
  SoftLineBreak          \n
  HardLineBreak          \n
  FencedCodeBlock        <pre><code class="...">...</code></pre>
  IndentedCodeBlock      <pre><code>...</code></pre>
  Link / Image           <a href="...">...</a>
  BlockQuote             <blockquote>...</blockquote>
  BulletList             • элемент\n• элемент
  OrderedList            1. элемент\n2. элемент
  ThematicBreak           \n---\n
  HtmlInline / HtmlBlock  экранированный текст (не интерпретируется)
  ~strikethrough~        не поддерживается (проходит как текст)

Особенности:
- Одинарный \n между параграфами: два Paragraph в markdown
  разделены пустой строкой, но визитор добавляет \n только в конец
  каждого параграфа — двойной перенос не возникает.

- Заголовки всех уровней (h1–h6) рендерятся одинаково (<b>...</b>).

- <br> не используется (Telegram не поддерживает), вместо него \n.

- Raw HTML из markdown-источника экранируется, а не интерпретируется.


2. toTextBlock() — HTML-фрагменты → дерево TextBlock
-----------------------------------------------------

Список TGHtmlPart преобразуется в рекурсивную структуру TextBlock,
которая упрощает последующую нарезку на сообщения без разрыва
посередине слова или HTML-тега.

  TextBlock = Text("...")                     листовой узел
            | Blocks(first, [Next, ...])      составной узел
            где Next = (prefix, content)       разделитель + вложенный блок

Алгоритм:
  а) HTML-фрагменты проходят «как есть», текстовые — разбиваются
     на сегменты по границе whitespace/non-whitespace (каждое слово
     и пробельный промежуток — отдельный элемент).

  б) Сегменты сворачиваются в Blocks:
     - не-whitespace → content (Text)
     - whitespace   → prefix для следующего content

  в) Два прохода splitBySeparator:
     - Проход 1: разделители с \n остаются соседями (Next),
                 пробелы схлопываются внутрь предыдущего блока.
     - Проход 2: разделители с 2+ \n остаются соседями,
                 одиночные \n — схлопываются.

  Итоговая структура:
    Blocks                       ← разделение по \n\n (если есть)
    ├── first: Blocks            ← разделение по \n и пробелам
    │   ├── first: Text("a")
    │   └── next: [Next(" ", Text("b")), Next("\n", Text("c"))]
    └── next: [
        Next("\n\n", Blocks(...))
    ]

Особенности:
- Ведущие и хвостовые пробелы отбрасываются (каждое сообщение
  начинается и заканчивается не-whitespace символом).
- \r и \r\n нормализуются в \n ещё на этапе парсинга commonmark.
- \u00A0 (неразрывный пробел) считается обычным символом, не whitespace.


3. chunk() + toText() — дерево → список строк
-----------------------------------------------

Дерево TextBlock рекурсивно нарезается на части не длиннее maxLength
(по умолчанию 4096). Нарезка происходит по границам Text-узлов — слово
или HTML-тег не разрываются, если это возможно. Если один Text-узел
длиннее лимита — он режется посимвольно.

  Text("оченьдлинноеслово")          limit=3
  → Text("оче") + Text("ньд") + ...

  Blocks(first=Text("a"),            limit=2
          next=[Next(" ", Text("b"))])
  → ["a", "b"]    (пробел-разделитель отброшен)

  Blocks(first=Text("ab"),           limit=2
          next=[])
  → ["ab"]         (помещается)


Затем toText() рекурсивно собирает каждый чанк в плоскую строку:

  Text("hello")                           → "hello"
  Blocks(Text("a"), [Next(" ", Text("b"))]) → "a b"


Итоговая функция
=================

  String.mdToTGMessages(maxLength = 4096): NonEmptyList<String>?

  null — если после преобразований не осталось значимого текста
         (пустая строка, только whitespace).
