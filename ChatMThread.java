package sd15;
import java.net.*;
import java.sql.*;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.io.*;

public class ChatMThread extends Thread {
	static ArrayList<ChatMThread> member; // クライアントメンバーを覚えておく
	static HashMap<String, String> userTable = new HashMap<>(); // userTable.put("userTest", "higeprof");
	static HashMap<String, Boolean> loginTable = new HashMap<>(); // 現在ログインしているユーザを格納

	Socket socket = null;
	String userName; // インスタンスにつけられるユーザ名
	BufferedWriter out = null;
	BufferedReader in = null;
	static SimpleDateFormat df = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");

	public ChatMThread(Socket s) {
		super("ChatMThread");
		socket = s; //System.out.println("クライアントとのsocketが作られました: "+socket.getPort());
		if (member==null) member = new ArrayList<ChatMThread>();
		member.add(this);
	}

	private Connection connect() {
//		String url = "jdbc:sqlite:S:/sqlite-tools-win32-x86-3240000/chatlog.sqlite3"; // windows
		String url = "jdbc:sqlite:/Users/cookey/eclipse-workspace/chatlog.sqlite3"; // mac
		Connection conn = null;
		try {
			conn = DriverManager.getConnection(url);
		} catch (SQLException e) {
			System.out.println(e.getMessage());
		}
		return conn;
	}

	public void doCommand(String sql) {
		try {Connection conn = this.connect();
		Statement stmt = conn.createStatement();
		//		int num =
		stmt.executeUpdate(sql);
		stmt.close();
		} catch (SQLException e) {
			System.out.println(e.getMessage());
		}
	}
	public ArrayList<String> getSelect(String sql) { // 引数のSQL文を実行し結果をArrayListで返すメソッド
		ArrayList<String> result = new ArrayList<>();
		try (Connection conn = this.connect();
				Statement stmt = conn.createStatement();
				ResultSet rs = stmt.executeQuery(sql)){
			if (rs.isBeforeFirst() == false) { // 結果がないとき
				return result;
			} else { // 結果が入っているのでrs.next()を実行
				while (rs.next()) {
					result.add(rs.getString(1) + " " + rs.getString(2) + " " + rs.getString(3)); // 1行に3列の情報
				}
			}
			rs.close(); stmt.close();
			return result;
		} catch(SQLException e) {
			System.out.println(e+"\ngetSelectメソッドで例外が起きました");
		}
		return null;
	}
	private void initUsr() { // ログインできるユーザの作成
		userTable.put("userTest", "higeprof");
		userTable.put("kouki", "0710");
		userTable.put("doraemon", "future2112");
		userTable.put("nobita", "1234");
		userTable.put("koma", "5678");
		userTable.put("hiyori", "0202");
		userTable.put("moca", "usagi");
	}

	@Override
	public void run() {
		try{
			initUsr(); // ユーザ認証用Map作成
			connect();
			out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
			in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
			String fromClient; // クライアントから送られてきた内容を一時的に格納する変数
			String message[]; // 空白区切りで分割し配列に格納
			String user = null, pass = null;
			while ((fromClient = in.readLine()) != null) {
				message = fromClient.split(" ", 0);
				user = message[1];
				pass = message[3];
				System.out.println("user:"+user+"_"+"pass:"+pass+"_");
				if (userTable.containsKey(user) && userTable.get(user).equals(pass)) { // ユーザ名とパスワードが一致(存在しないユーザのときfalseとなる)
					System.out.println("認証成功");
				} else { // 認証されないとき
					System.out.println(user + "へ送信： 100 password invalid");
					out.write("100 password invalid"); out.newLine(); out.flush();
					disconnection();
					return;
				}
				if (loginTable.containsKey(user)) { // ログイン中ユーザを格納するMapに存在しているか
					System.out.println(user + "へ送信： 101 multiple login");
					out.write("101 multiple login\n"); /*out.newLine(); */out.flush();
					disconnection();
					return;
				}
				System.out.println("多重ログイン認証成功");
				break;
			}

			out.write("0 login succeed"); out.newLine(); out.flush(); // ここまでreturnされていなければ認証成功
			System.out.println("memberCnt: " + member.size()); // 接続されているクライアント数を出力
			// 新しいユーザの参加を全員に知らせる
			this.userName = user;
			String sql = "select userName, time, time from log where userName=\""+this.userName+"\" and type=0 and chat=\"login\" order by time desc limit 1"; // このユーザが最後にログインした時間

			try {
				ArrayList<String> loginResult = getSelect(sql);
				if (loginResult.size() != 0) { // 以前のログインの記録がある
					String[] lastLoginMsg = loginResult.get(0).toString().split(" ");
					sendAll("login " + this.userName + " " + dateToString( Long.parseLong(lastLoginMsg[1]))); // 前回のログイン日時も伝える (login userName 2018/07/27 16:51:28)
					System.out.println("以前のログイン記録あり: login " + this.userName + " " + dateToString( Long.parseLong(lastLoginMsg[1])));
				} else { // 記録がない(はじめてのログイン)
					System.out.println("以前のログイン記録なし: login " + this.userName + " 0" );
					sendAll("login " + this.userName + " 0 0"); // 前回のログイン日時は0とする 初めての人に login userName 0 0 と送る人もいる
				}
			} catch (Exception e) {}

			recordDatabase(this.userName, 0, "login"); // ログインしたことをデータベースへ記録
			loginTable.put(this.userName, true); // ログイン中ユーザを記録

			// 前回の自分のログインの通知
			// select hostip, max(time), type from log where userName="userTest" and type=0 and chat="login" and time not in (select max(time) from log where userName="userTest"); ユーザの前回のログイン時刻
			sql = "select hostip, max(time), type from log where userName=\""+this.userName+"\" and type=0 and chat=\"login\" and time not in (select max(time) from log where userName=\""+this.userName+"\")"; // 最後から2番目の記録
			ArrayList<String> prevloginResult = getSelect(sql);
			System.out.println(prevloginResult.get(0).toString());
			try {
				message = prevloginResult.get(0).toString().split(" ", 0);
				System.out.println(message[2]);
				if (message[2].equals("0")) { // 結果なしの時nullが返ってきてしまうのでtypeが0の条件文
					out.write("prevlogin "+message[0]+" "+dateToString(Long.parseLong(message[1])));
					System.out.println("prevlogin "+message[0]+" "+dateToString(Long.parseLong(message[1])));
				} else { // 前回のログイン記録がない
					out.write("prevlogin 0 0");
					System.out.println("prevlogin 0 0");
				}
				out.newLine(); out.flush();
			} catch (Exception e) {}

			// 現在のログインユーザを通知
			out.write("curuser "+loginTable.size()); // loginTableの要素数はユーザ数を表す
			for (String key : loginTable.keySet()) {
				out.write(" "+key);
			}
			out.newLine(); out.flush(); // (curuser 2 userTest nobita)
			// 過去のチャットの通知
			String oldChat = "";
			sql = "select userName, time, chat from log where type=1 order by time desc limit 10";
			ArrayList<String> oldChatResult = getSelect(sql);
			if (oldChatResult.size() != 0) { // oldchatが存在するとき
				for (int i = oldChatResult.size(); i > 0; i--) {
					message = oldChatResult.get(i-1).toString().split(" ", 0);
					oldChat = oldChatResult.get(i-1).toString().substring(message[0].length()+1+message[1].length()+1); // [oldchat userName ]より先の文字列が格納される
					out.write("oldchat "+message[0]+" "+(i)+" "+dateToString(Long.parseLong(message[1]))+" "+oldChat); out.newLine(); // (oldchat 1 userTest 最新のテストメッセージ)
				}
			} else { // 過去のチャット記録がないとき
				out.write("oldchat 0 0 0 0"); out.newLine();
			}
			out.flush();

			int type = -1; // 無効な値を初期値とする
			String chat = ""; // 無効な値を初期値とする

			while ((fromClient = in.readLine()) != null) { // クライアントからの発言を読み込む
				message = fromClient.split(" ", 0); // 配列に区切る
				if (message[0].equals("logout")) {
					disconnection(); // そのクライアントとの接続を終了するメソッド
					System.out.println("disconnected.");
					type = 0; // DBに記録する値
					chat = "logout"; // DBに記録する値
				} else {
					type = 1; // DBに記録する値
					System.out.println(fromClient);
					chat = fromClient.substring(message[0].length()+1+message[1].length()+1); // chatとなる部分の切り出し
					System.out.println("chat : "+chat);
					sendAll(fromClient); // その発言を全てのクライアントへ送信
				}
				recordDatabase(this.userName, type, chat);// 受信内容をデータベースへ記録
				//				System.out.println(sql);
				//				doCommand(sql);
				if (message[0].equals("logout")) {
					sql = "SELECT userName, max(time), max(time) FROM log WHERE chat=\"login\" and userName=\"" + userName+"\""; // このクライアントの今回のログイン時刻
					System.out.println("実行SQL1:"+sql);
					message = getSelect(sql).get(0).toString().split(" ", 0);
					String dateStr = dateToString(Long.parseLong(message[1])); // ログインした日時の秒をフォーマットされた文字列に変換 (2018/07/27 16:51:28)
					sql = "select userName, count(*), count(*) from log where userName=\""+userName+"\" and type=1 and time>=(SELECT max(time) FROM log WHERE userName=\""+userName+"\" and type=0 and chat=\"login\")"; // userTestが最後にログインした時刻以降のチャット数
					System.out.println("実行SQL2:"+sql);
					message = getSelect(sql).get(0).toString().split(" ", 0);
					int chatCnt = Integer.parseInt(message[1]);
					sendAll("logout "+this.userName+" "+dateStr+" "+chatCnt); // あるユーザがログアウトしたことを通知 (logout userTest 2018/07/27 16:51:28 3
					break; // while文を抜けて接続終了する
				}
			}
		} catch (IOException e) {
			// クライアント接続が相手側から切断されたときにここにくるかもしれない
			System.out.println("runメソッド実行中例外: " + e);
			disconnection();
		}
		disconnection();
	}
	public static String dateToString(long time) {
		Date date = new Date(time*1000);
		return df.format(date); // "yyyy/MM/dd HH:mm:ss"にフォーマットされた文字列を返す
	}
	private void recordDatabase(String userName, int type, String chat) { // ユーザ名、type、チャット部分を引数にとり、DBに挿入する
		String sanitized = replaceForSql(chat); // 不正な文字を変換する
		System.out.println("sanitized: "+sanitized);
		String sql = "INSERT INTO log VALUES("+Instant.now().getEpochSecond()+", \""+socket.getInetAddress()+"\", "+socket.getPort()+", \""+userName+"\", \""+type+"\", \""+sanitized+"\")";
		System.out.println("データベースへ記録されるSQL: "+sql);
		doCommand(sql);
	}
	public static String replaceForSql(String str) { // sql文になるように変な文字は変換する
		String init = str; // init == 処理前の文字列 str == 処理後の文字列
		if (null == str || "".equals(str)) {
			System.out.println("replaceForSql 文字列がnullか空白");
			return str;
		}
		str = str.replaceAll("\"", "\"\"");
		str = str.replaceAll("'", "\'\'");
		if (init.equals(str)) System.out.println("sql文字が含まれていませんでした");
		else System.out.println("sqlされるべき文字が含まれていました");
		return str;
	}
	private void sendAll(String fromClient) throws IOException { // 引数fromClientを全てのログイン中クライアントのストリームへ出力
		for(ChatMThread client : member){
			System.out.println(client.userName.toString());
			if (loginTable.containsKey(client.userName)) { // ログイン中なら
				System.out.println("sendAll_to: "+client.userName + "_contents: " +fromClient);
				client.out.write(fromClient); client.out.newLine(); client.out.flush();
			}
		}
	}
	private void disconnection() { // ChatMThreadを終了するときに実行
		try {
			member.remove(this); // 自分自身をListから消して、メッセージが送られないようにする
			loginTable.remove(this.userName); // このユーザをログイン中ユーザから削除する
			in.close();
			out.close();
			socket.close();
		} catch (IOException e) {e.printStackTrace();}
	}
}
