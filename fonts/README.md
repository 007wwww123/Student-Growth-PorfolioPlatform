# 生产字体

PDF 导出使用 PDFBox，需要 TTF 中文字体。把 `simhei.ttf`（或其它合法授权的 TTF）放在本目录，
`docker-compose.yml` 会挂载到容器 `/fonts/simhei.ttf`。

字体文件不纳入仓库。
