# Pivot 键盘 · Pivot Keyboard

一款 Windows 10 Mobile 风格的 Android 键盘，支持中文拼音和英文。

An Android keyboard in the manner of Windows 10 Mobile, for Chinese (Pinyin) and English.

## 功能

- **外观照着真机做**：深色时键盘近黑，字母键深灰，shift、退格、`&123`、表情键浅一档，回车键与键盘反色；细线图标（↑、退格、放大镜、笑脸）；按下时键变成主题色，上方显示放大的字母；长按出现重音和数字条；`&123` 角上的三个小点；空闲时候选栏左侧只有一个麦克风。字体是微软开源的 Selawik（Segoe UI 的开源替代）。
- **拼音**：全拼或微软双拼（在设置中选择；双拼时 shift 键变成 `ing` 键，按键上标出韵母），自动切分音节并在输入框里显示（`wo'men'shi`）；首选是整句（`womenshizhongguoren` → 我们是中国人）；支持简拼（`nh` → 你好）和不完整的音节（`zhongg` → 中国）；空格选首个，点任意候选选它，剩下的拼音继续选；候选栏末尾的箭头展开全部候选；回车输入字母本身。
- **简/ENG 键**：点一下切换中英文（英文默认小写），长按出现设置和语言的方块菜单。中文时逗号句号是 `，` `。`，符号页是中文标点。
- **联想**：选完一个词，候选栏接着给出常跟在后面的词，并记住你自己的搭配。
- **语音输入**：点麦克风开始听写，说的话直接进入输入框，一直听到再点一次麦克风。
- **退格**：按住连续删除（带连续音效）；从退格键向上滑清空全部文字，向下滑恢复。
- **光标圆钮**：拖动 z 和 x 之间的圆钮可上下左右移动光标。
- **英文**：补全、拼写纠正（考虑键盘上相邻的键、重复字母、颠倒的字母、漏掉的撇号，`dont` → `don't`），纠正后马上按退格可以撤销；句首自动大写，双击空格输入句号。
- **表情**面板，按类别排列。
- **记住你选的词**，只保存在手机上，可以一键清除。
- **设置**也是 Windows 10 Mobile 的样子：pivot 标题、开关、下拉框打开的全屏列表、细滑块、主题色方块。

需要 Android 8.0 或更高版本。

## 构建

```bash
./gradlew assembleDebug
```

词库由 `tools/build_dictionaries.py` 从上述原始数据生成（生成结果已在 `app/src/main/assets` 中）。

## 许可证

Apache License 2.0，见 [LICENSE](LICENSE)。

- 词库：Android 开源项目 PinyinIME 和 LatinIME（Apache License 2.0）；jieba 词频（MIT）；mozillazg/phrase-pinyin-data 与 pinyin-data（MIT）。约 23 万条中文词。
- 字体：Selawik，Microsoft，SIL Open Font License 1.1，见 [licenses/Selawik-OFL-1.1.txt](licenses/Selawik-OFL-1.1.txt)。

Windows 和 Windows Phone 是微软公司的商标。本项目与微软无关。
