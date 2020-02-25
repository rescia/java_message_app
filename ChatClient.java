package sd15;
import java.io.*;
import java.net.*;
import javafx.application.Application;
import javafx.event.*;
import javafx.geometry.*;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.layout.*;
import javafx.stage.Stage;

public class ChatClient extends Application {
	static TextArea txtArea = new TextArea(); // GUIテキストエリア
	static Socket kaiwaS = null;
	static BufferedReader in = null;
	static BufferedWriter out = null;
	static String userName; // このクライアントのユーザ名
	static String password;
	static String ipAddr;
	static int port;
	public static void main(String[] args) throws IOException {

		// 例: java ChatClient 192.168.2.200 50007 userName password
		if (args.length != 4) {
			System.out.println("引数を正しく指定してください\n"
					+ "ipアドレス ポート番号 ユーザ名 パスワード\n");
			System.exit(1);
		}
		ipAddr = args[0];
		port = Integer.parseInt(args[1]);
		userName = args[2];
		password = args[3];
		System.out.println("ipAddr: " + ipAddr + " port: " + port + " userName: " + userName);
		// サーバへの接続
		try{
			kaiwaS = new Socket(ipAddr, port);
			in = new BufferedReader(new InputStreamReader(kaiwaS.getInputStream()));
			out = new BufferedWriter(new OutputStreamWriter(kaiwaS.getOutputStream()));
			String sendStr = "user " + userName + " pass " + password;
			out.write(sendStr);
			System.out.println("sendServerStr: " + sendStr); // サーバへユーザ名とパスワードを送る
			out.newLine();
			out.flush();
		} catch (UnknownHostException e) {
			System.out.println("ホストに接続できません。");
			System.exit(1);
		} catch (IOException e) {
			System.out.println("IOコネクションを得られません。");
			System.exit(1);
		}
		// 認証の完了を待つ
		String fromServer; // サーバから送られてきた内容を一時的に格納する変数
		while ((fromServer = in.readLine()) != null) {
			System.out.println("fromServer is_" + fromServer + "_");
			if (fromServer.equals("100 password invalid")) {
				System.out.println("ユーザ "+userName+" のパスワードが間違っています");
				clientEnd(1);
				System.out.println("clientEndを完了しました");
				return;
			} else if (fromServer.equals("101 multiple login")) {
				System.out.println("ユーザ "+userName+" さんはすでにログインしています");
				clientEnd(1);
				return;
			} else if (fromServer.equals("0 login succeed")) {
				// 別スレッドでサーバからの受信を待つ
				new Thread(()->{
					String fromSv; // サーバから送られてきた内容を一時的に格納する変数
					String message[];
					try {
						while ((fromSv = in.readLine()) != null) {
							System.out.println("サーバからのメッセージ: "+fromSv);
							message = fromSv.split(" ", 0); // str1 str2 str3 ... -> message[0], message[1], message[2]
							if (message[0].equals("login")) { // login userName 2018/07/20 00:00:55
								txtArea.appendText(message[1] + " さんがログインしました。");
								if (!(message[2].equals("0"))) txtArea.appendText("前回のログインは"+message[2]+" "+message[3]+"に行われました\n");
								else txtArea.appendText("はじめてのログインです\n");
							}
							else if (message[0].equals("chat")) {
								String chat = fromSv.substring(message[0].length()+1+message[1].length()+1); // 空白をチャットできるようにする
								txtArea.appendText(message[1] + " さん > " + chat + "\n");
							}
							else if (message[0].equals("logout")) txtArea.appendText(message[1]+" さんが退出しました。"+message[2]+" "+message[3]+"にログインして以来、"+message[4]+"個の発言をしました。\n");
							else if (message[0].equals("prevlogin")) {
								if (message[1].equals("0")) txtArea.appendText("はじめてのログインです\n");
								else txtArea.appendText("前回ログイン時IPアドレス: "+message[1]+" 前回ログイン時刻: "+message[2]+" "+message[3]+"\n");
							}
							else if (message[0].equals("curuser")) {
								txtArea.appendText("現在ログイン中の人数: "+message[1]+"\n");
								for (int i = 0; i < Integer.parseInt(message[1]); i++) {
									txtArea.appendText("ログイン中ユーザ"+(i+1)+": "+message[i+2]+"\n");
								}
							}
							else if (message[0].equals("oldchat")) {
								if (message[1].equals("0") && message[2].equals("0") && message[3].equals("0") && message[4].equals("0")) {
									txtArea.appendText("はじめてのチャットです\n");
								} else {
									String oldChat = fromSv.substring(message[0].length()+1+message[1].length()+1+message[2].length()+1+message[3].length()+1+message[4].length()+1); // 空白をチャットできるようにする
									System.out.println(oldChat);
									txtArea.appendText("過去のチャット"+message[2]+" "+message[1]+" "+message[3]+" "+message[4]+" "+oldChat+"\n");
								}

							}
							else txtArea.appendText(fromSv + "\n");
						}
						in.close();System.out.println("inClosed.(ChatClientのnew Thread)");
						out.close();
					} catch (IOException e) {System.out.println("newThreadIOException");}
				}).start();
				launch(args);
				// GUIを消したらChatClientを閉じる
				clientEnd(0);
			}
		}
	}
	private static void clientEnd(int status) { // status==1は接続終了処理、0はログアウトを送って接続終了処理
		// ChatClientを閉じる
		try {
			if (status == 0) out.write("logout"); out.newLine(); out.flush();
			out.close();
			in.close();
			kaiwaS.close();
		} catch (IOException e) {e.printStackTrace();}
		System.out.println("ChatClientを閉じる動作を完了しました");
		// ChatMThreadを閉じる
		ChatMThread.interrupted();
		// メッセージ受け付け用スレッドが動いているので一緒に終了させる
		System.exit(0);
	}

	@Override
	public void start(Stage primaryStage) throws Exception {
		System.out.println("newThread(GUI)");
		// 追加するアイテム
		TextField txtField = new TextField();
		Button chatBtn = new Button("発言");
		MenuBar menuBar = new MenuBar();
		Menu menuFile = new Menu("File");
		Menu menuDisplay = new Menu("Display");

		// メニューバーの作成
		MenuItem clear = new MenuItem("Clear");
		clear.setOnAction((ActionEvent t) -> {
			txtField.clear();
		});
		MenuItem exit = new MenuItem("Exit");
		exit.setOnAction((ActionEvent t) -> {
			clientEnd(0);
		});
		MenuItem member = new MenuItem("Member");
		member.setOnAction((ActionEvent t) -> {
			Alert alert = new Alert(AlertType.NONE, "", ButtonType.OK);
			alert.setTitle("Display-Member");
			alert.getDialogPane().setHeaderText("サーバとクライアントの情報");
			alert.getDialogPane().setContentText("接続先: "+ipAddr+" "+port
					+"\nこのクライアント:"+kaiwaS.getLocalAddress()+" "+kaiwaS.getLocalPort()
					+"\nユーザ名: "+userName);
			ButtonType button  = alert.showAndWait().orElse( ButtonType.CANCEL );
		});
		MenuItem art = new MenuItem("Art");
		art.setOnAction((ActionEvent t) -> {
		});

		menuFile.getItems().addAll(clear, new SeparatorMenuItem(), exit);
		menuDisplay.getItems().addAll(member, art);
		menuBar.getMenus().addAll(menuFile, menuDisplay);

		// アイテムの設定
		txtArea.setWrapText(true);
		txtArea.setPrefHeight(600);
		txtArea.setEditable(false);
		txtField.setPrefWidth(550);
		chatBtn.setPrefWidth(50);
		HBox hb = new HBox(txtField, chatBtn);
		hb.setAlignment(Pos.TOP_LEFT);
		hb.setPrefWidth(600);

		txtField.setOnAction((event)->{ // txtFieldの動作
			try {
				System.out.println("event: "+event);
				out.write("chat "+userName+" "+txtField.getText()); out.newLine(); out.flush();
			} catch (IOException e) {e.printStackTrace();}
			txtField.clear();
		});
		txtArea.setFocusTraversable(false);

		chatBtn.setOnAction((event)->{ // chatBtnボタン
			try {
				out.write("chat "+userName+" "+txtField.getText()); out.newLine(); out.flush();
			} catch (IOException e) {e.printStackTrace();}
			txtField.clear();
		});

		// 全てを統合
		VBox root = new VBox();
		root.getChildren().addAll(menuBar, txtArea, hb);
		root.setAlignment(Pos.TOP_CENTER);

		// GUIの定義
		Scene scene = new Scene(root, 600, 600);
		primaryStage.setTitle("ChatClient");
		primaryStage.setScene(scene);
		primaryStage.sizeToScene();
		primaryStage.show();
		primaryStage.setOnCloseRequest(ev -> { // GUIのウィンドウを消したらプログラムを終了
			//			System.out.println("primaryStageCloseRequest");
			//			System.exit(0);
		});
	}
}
