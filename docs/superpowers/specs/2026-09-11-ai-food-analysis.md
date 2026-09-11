# AI 食物分析功能设计

## 目标

在 GymStatistics 中加入独立的 AI 食物分析工具，支持拍摄食物、从相册选择食物相关图片、识别食物或营养标签、编辑每100g营养数据，并将结果保存到独立的食物分析历史；不写入当前训练日期，也不实现二维码或条形码营养查询。

## 用户流程

1. 主页面进入“AI食物分析”。
2. 用户选择“拍摄照片”或“从相册选择”。相册使用系统图片选择器，不申请整库存储权限。
3. 首次分析前配置 DeepSeek API Key；Key 使用 Android Keystore 加密保存。
4. 应用压缩图片并发送到 DeepSeek，自动判断是食物照片、营养成分表、配料表或混合图片。
5. 结果页面展示材料、重量、每100g营养数据、总重量和整份总营养；用户可独立编辑材料重量及每项营养素。
6. 用户点击保存后写入单独的 `food-analysis-history.json`；分析失败或未确认的结果不自动保存。

## 数据约束

- 所有营养数值必须按每100g表达；字段名称明确固定单位。
- 实际营养值为 `per100gValue * weightG / 100`。
- 总重量为已知材料重量之和；模型返回的总量只作为初始值，应用保存前重新计算。
- 无法识别或无法可靠换算的数值使用 null，不让模型编造精确数据。
- 标签原始单位、净含量、份量和换算说明保留在结果中。
- 原始照片默认不保存，分析结束后删除相机临时文件；相册 URI 只在当前分析期间使用。
- 二维码和条形码不作为数据源；图片中出现时忽略。

## DeepSeek 请求

- API 基址：`https://api.deepseek.com`
- 当前模型参数：`deepseek-flash`
- 使用图片内容和 JSON response format。
- API Key 不进入日志、训练数据或分析历史。
- 返回内容经过 JSON 解析和字段校验后才能进入编辑页面。

## 结果字段

```text
FoodAnalysisRecord
  id, createdAt, foodName, imageType, ingredientsText
  totalWeightG, totalWeightSource, labelInfo
  items, uncertaintyNote

FoodAnalysisItem
  name, weightG, weightSource
  nutritionPer100g
  nutritionSource, confidence, isAggregate, note

NutritionPer100g
  energyKcal, energyKj, proteinG, fatG, carbohydrateG
  fiberG, sugarsG, sodiumMg, cholesterolMg
```

## 界面范围

- 食物分析入口页：图片来源按钮、API 设置、历史入口、加载和错误状态。
- API 设置页：输入、保存、删除 Key。
- 分析结果页：食物信息、材料编辑、总重量、总营养和保存按钮。
- 历史页：按时间倒序显示记录，支持打开编辑和删除。

## 明确不做

- 不把分析结果写入训练记录。
- 不扫描二维码或条形码查询商品营养。
- 不保存原始食物照片。
- 不新增 CameraX、二维码扫描或食品数据库依赖。
