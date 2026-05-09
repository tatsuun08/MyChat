# MyChat
セキュアで高速なチャットを実装しながら，モダンなアーキテクチャについて学習する

## 概要
### 使用技術
フロントエンド：
* Kotlin
* Android SDK

ライブラリ
* Retrofit API通信
* Gson JSON変換
* Room ローカルDB処理
* Croutines 非同期処理
* Navigation Component

バックエンド：
* Go言語

ライブラリ
* net/http
* GORM

データベース
* PostgreSQL（サーバー）
* SQLite（ローカル）

### 基本機能
<img src="./img/login.png" width="30%"> <img src="./img/create_room.png" width="30%"> <img src="./img/room_list.png" width="30%">
<img src="./img/send_message.png" width="30%">  <img src="./img/invite_user.png" width="30%"> <img src="./img/load_other_user_message.png" width="30%">


## 実装済み
* クライアントからメッセージを送信するときにAES暗号化

## ToDo
* ルームに所属しているクライアント間で暗号鍵を共有する
* 通信環境がないときにメッセージを送信するとクラッシュする
* パスワード認証によるログイン
* ルームの削除機能
* メッセージの削除機能
