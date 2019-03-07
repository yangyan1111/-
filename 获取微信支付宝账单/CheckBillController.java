package com.aigosky.order.controller;

import java.io.*;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import com.aigosky.pojo.*;
import com.alipay.api.AlipayClient;
import com.alipay.api.DefaultAlipayClient;
import com.alipay.api.request.AlipayDataDataserviceBillDownloadurlQueryRequest;
import com.alipay.api.response.AlipayDataDataserviceBillDownloadurlQueryResponse;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

import com.aigosky.common.utils.IDUtils;
import com.github.wxpay.sdk.WXPayUtil;

/**
 * 系统对账
* @ClassName: CheckBillController 
* @Description: TODO 
* @author yangyan
* @date 2018年5月12日 上午9:19:59
 */
@Controller
@RequestMapping("/checkBill")
public class CheckBillController {
	
	private Logger logger_paymentcheck = Logger.getLogger("logger_paymentcheck");
	//公众账号ID
	@Value("${WX_APPID}")
	private String WX_APPID;
	//微信支付分配的商户号
	@Value("${WX_MCH_id}")
	private String WX_MCH_id;
	//微信支付key
	@Value("${WX_KEY}")
	private String WX_KEY;

	@Value("${Alipay.URL}")
	private String url;

	@Value("${Alipay.APPID}")
	private String appid;
	//应用私钥
	@Value("${Alipay.RSA_PRIVATE_KEY}")
	private String rsaPrivateKey;
	//应用公钥
	@Value("${Alipay.PUBLIC_KEY}")
	private String publicKey;
	//支付宝公钥
	@Value("${Alipay.ZFB_PUBLIC_KEY}")
	private String zfb_publicKey;

	@Value("${Alipay.CHARSET}")
	private String charset;

	@Value("${Alipay.SIGNTYPE}")
	private String signType;

	@Value("${Alipay.FORMAT}")
	private String format;
	//保存支付宝账单的路径
	@Value("${ZFB_FILE_PATH}")
	private String ZFB_FILE_PATH;

	/**
	 * 定时获取第三方的交易账单
	 * @throws Exception
	 */
	public void downloadApiAccount() throws Exception {
		//获取微信账单
		downloadwxBill();
		//获取支付宝账单
		downloadzfbBill();
	}


	/**
	 * 获取微信账单
	 */
	public void downloadwxBill() {
		try {
			Map<String, String> dataMap = new HashMap<String, String>();
			dataMap.put("appid", WX_APPID);//appid
			dataMap.put("mch_id", WX_MCH_id);// 商户号
			dataMap.put("nonce_str", IDUtils.getUUID());// 随机字符串
			// 对账日期计算
			Date date = new Date();
			Calendar calendar = Calendar.getInstance();
			calendar.setTime(date);
			calendar.add(Calendar.DAY_OF_MONTH, -1);
			date = calendar.getTime();
			// 设置要获取到什么样的时间
			SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
			// 获取String类型的时间
			String createdate = sdf.format(date);
			String requestDate = createdate.replaceAll("-", "");
			dataMap.put("bill_date", requestDate);// 对账单日期
			dataMap.put("bill_type", "SUCCESS");// 账单类型
			// 字典顺序排列
			List dictionary = dictionary(dataMap);
			StringBuffer sb = new StringBuffer();
			for (int j = 0; j < dictionary.size(); j++) {
				String string = dataMap.get(dictionary.get(j));
				sb.append(dictionary.get(j) + "=" + string + "&");
			}
			sb.append("key="+WX_KEY);
			String upperCase = WXPayUtil.MD5(sb.toString()).toUpperCase();
			dataMap.put("sign", upperCase);
			String requestWithoutCert = requestWithoutCert("https://api.mch.weixin.qq.com/pay/downloadbill", dataMap, 1000,
					1000);
			String replace2 = requestWithoutCert.replace(",", "");
			String newStr = requestWithoutCert.replaceAll("`", " "); // 去空格
			String replace = newStr.replace(" ", "");
			String[] tempStr = replace.split(","); // 数据分组4 5
			int a = 1;
			List<ApiAccount> wxAccountsList = new ArrayList<ApiAccount>();
			if (tempStr.length != 1){
				// 开始核对账单
				String[] split = replace2.split("`");
				// 根据索引获取元素中的值
				int b = 1;
				for (int i = 1; i < split.length; i++) {
					ApiAccount apiAccount = new ApiAccount();
					// 订单号
					if (a == i) {
						String processDate = split[i];       //订单处理时间
						String businessId = split[i+2];     //商户号
						String wxOrderId = split[i+5];     //微信订单号
						String userOrderId = split[i+6];  //商户订单号
						String state = split[i+9];       //交易状态
						String payAmout = split[i+12];  //支付金额
						String goodsName = split[i+14];  //商品名称
						String txFee = split[i+16];    //手续费
						String rate = split[i+17];    //费率
						String[] split2 = rate.split("%");
						a = i + 18;
						SimpleDateFormat sdff = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
						Date timeDate = sdff.parse(processDate);
						BigDecimal bigPayamout = new BigDecimal(payAmout);
						BigDecimal bigTxFee = new BigDecimal(txFee);
						//数据处理


						if(split2.length > 1 && split2[1].contains("总交易单数")){
							break;
						}
					}
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}


	/**
	 * 字典表升序排列
	 * @param map
	 * @return
	 */
	public static List dictionary(Map map) {
		List list = new ArrayList();
		Iterator iterator = map.entrySet().iterator();
		while (iterator.hasNext()) {
			Entry entry = (Entry) iterator.next();
			list.add(entry.getKey());
		}
		Collections.sort(list);
		return list;
	}
	
	/**
	 * 发送微信请求
	 * @param strUrl
	 * @param reqData
	 * @param connectTimeoutMs
	 * @param readTimeoutMs
	 * @return
	 * @throws Exception
	 */
	public static String requestWithoutCert(String strUrl, Map<String, String> reqData, int connectTimeoutMs,
			int readTimeoutMs) throws Exception {
		String UTF8 = "UTF-8";
		String reqBody = WXPayUtil.mapToXml(reqData);
		URL httpUrl = new URL(strUrl);
		HttpURLConnection httpURLConnection = (HttpURLConnection) httpUrl.openConnection();
		httpURLConnection.setDoOutput(true);
		httpURLConnection.setRequestMethod("POST");
		httpURLConnection.setConnectTimeout(connectTimeoutMs);
		httpURLConnection.setReadTimeout(readTimeoutMs);
		httpURLConnection.connect();
		OutputStream outputStream = httpURLConnection.getOutputStream();
		outputStream.write(reqBody.getBytes(UTF8));

		// 获取内容
		InputStream inputStream = httpURLConnection.getInputStream();
		BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream, UTF8));
		final StringBuffer stringBuffer = new StringBuffer();
		String line = null;
		while ((line = bufferedReader.readLine()) != null) {
			stringBuffer.append(line);
		}
		String resp = stringBuffer.toString();
		if (stringBuffer != null) {
			try {
				bufferedReader.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		if (inputStream != null) {
			try {
				inputStream.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		if (outputStream != null) {
			try {
				outputStream.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		return resp;
	}


	public static void main(String[] args) {
		try {
//			new CheckBillController().downloadzfbBill();
			delFolder("D:/e2pai/zfbfile/");
//			File fileDir = new File("D:/Users/20886127687786110156_20190228.csv.zip");
//			new CheckBillController().zipDecompressing(fileDir,"D:/Users");
//			String aa = "http://dwbillcenter.alipay.com/downloadBillFile.resource?bizType=trade&userId=20886127687786110156&fileType=csv.zip&bizDates=20190228\n" +
//					"&downloadFileName=20886127687786110156_20190228.csv.zip&fileId=%2Ftrade%2F20886127687786110156%2F20190228.csv.zip&timestamp=1551433335&token=17e088cc1527a9432e7fe97ca8ba8079";
//			System.out.println(URLRequest(aa).get("downloadfilename"));
			BigInteger b = new BigInteger("-1888832");
			b = b.abs();
			System.out.println(b.toString());

		} catch (Exception e) {
			e.printStackTrace();
		}
	}


	/**
	 * 定时获取支付宝的交易账单
	 * @throws Exception
	 */
	public void downloadzfbBill() throws Exception {
		Calendar cal = Calendar.getInstance();
		cal.add(Calendar.DATE, -1);
		Date time=cal.getTime();
		String billdate = new SimpleDateFormat("yyyy-MM-dd").format(time);
		AlipayClient client = new DefaultAlipayClient(url, appid, rsaPrivateKey, this.format, charset, zfb_publicKey, signType);
		AlipayDataDataserviceBillDownloadurlQueryRequest request = new AlipayDataDataserviceBillDownloadurlQueryRequest();
		request.setBizContent("{" +
				"\"bill_type\":\"trade\"," +
				"\"bill_date\":\""+billdate+"\" }");
		AlipayDataDataserviceBillDownloadurlQueryResponse response = client.execute(request);
		//调用成功
		logger_paymentcheck.info("获取支付宝账单返回信息：日期" + billdate + ",code:" +
				response.getCode() + ",msg：" + response.getMsg() + ",bill_download_url： " +
				response.getBillDownloadUrl() + ",sub_code：" + response.getSubCode() + ",sub_msg：" + response.getSubMsg());
		if(response.isSuccess()){
			//将接口返回的对账单下载地址传入urlStr
			String code = response.getCode();
			if ("10000".equals(code)) {  //成功获取
				String urlStr = response.getBillDownloadUrl();
				//指定希望保存的文件路径
				String filePath = ZFB_FILE_PATH + URLRequest(urlStr).get("downloadfilename");
				logger_paymentcheck.info("开始保存账单,bill_download_url： " + response.getBillDownloadUrl() + ",保存路径："+ filePath);
				URL url = null;
				HttpURLConnection httpUrlConnection = null;
				InputStream fis = null;
				FileOutputStream fos = null;
				try {
					url = new URL(urlStr);
					httpUrlConnection = (HttpURLConnection) url.openConnection();
					httpUrlConnection.setConnectTimeout(5 * 1000);
					httpUrlConnection.setDoInput(true);
					httpUrlConnection.setDoOutput(true);
					httpUrlConnection.setUseCaches(false);
					httpUrlConnection.setRequestMethod("GET");
					httpUrlConnection.setRequestProperty("CHARSET", "UTF-8");
					httpUrlConnection.connect();
					fis = httpUrlConnection.getInputStream();
					byte[] temp = new byte[1024];
					int b;
					fos = new FileOutputStream(new File(filePath));
					while ((b = fis.read(temp)) != -1) {
						fos.write(temp, 0, b);
						fos.flush();
					}

					//解压文件zip
					File fileDir = new File(filePath);
					zipDecompressing(fileDir,ZFB_FILE_PATH);
					//解析文件并保存到数据库
					parsingFiles(URLRequest(urlStr).get("downloadfilename"));
				} catch (MalformedURLException e) {
					e.printStackTrace();
					logger_paymentcheck.info("开始保存账单出现异常： " + response.getBillDownloadUrl() + "," + e.getMessage());
				} catch (IOException e) {
					e.printStackTrace();
				} finally {
					try {
						if(fis!=null) fis.close();
						if(fos!=null) fos.close();
						if(httpUrlConnection!=null) httpUrlConnection.disconnect();
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
			}

		}
	}


	/**
	 * 解压文件zip
	 * @param zipFile 需要解压文件
	 * @param descDir 解压完成之后输出的文件夹
	 * @throws IOException
	 */
	private void zipDecompressing(File zipFile, String descDir) {
		BufferedInputStream Bin = null;
		ZipInputStream Zin = null;
		String serverName = System.getProperty("os.name");
		String serverSeparator = System.getProperty("file.separator");
		try {
			Charset gbk = Charset.forName("gbk");
			Zin = new ZipInputStream(new FileInputStream(zipFile),gbk);//输入源zip路径
			Bin = new BufferedInputStream(Zin);
			String Parent = descDir; //输出路径（文件夹目录）
			File Fout = null;
			ZipEntry entry;
			while((entry = Zin.getNextEntry())!=null && !entry.isDirectory()){
				//entry.setUnixMode(755);
				Fout=new File(Parent,entry.getName());
				if(!Fout.exists()){
					(new File(Fout.getParent())).mkdirs();
				}
				FileOutputStream out=new FileOutputStream(Fout);
				BufferedOutputStream Bout=new BufferedOutputStream(out);
				int b;
				while((b=Bin.read())!=-1){
					Bout.write(b);
				}
				Bout.close();
				out.close();
			}
		} catch (Exception e) {
			e.printStackTrace();
			logger_paymentcheck.info("解压文件zi出现异常：文件： " + zipFile+ "，异常信息：" + e.getMessage());
		} finally {
			if (Bin !=null) {
				try {
					Bin.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
			if (Zin != null) {
				try {
					Zin.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
	}

	/**
	 * 按顺序关闭流
	 */
	private void closeStream(BufferedReader bufferedReader, InputStreamReader inputStreamReader, InputStream inputStream) {
		try {
			if (bufferedReader != null) {
				bufferedReader.close();
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		if (inputStreamReader != null) {
			try {
				inputStreamReader.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		if (inputStream != null) {
			try {
				inputStream.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}


	/**
	 * 读取文件数据并存入数据库
	 */
	public void parsingFiles(String fileName) {
		String csvName="";
		String name = fileName.split("\\.")[0];
		File fileDir = new File(ZFB_FILE_PATH);
		InputStreamReader inputStreamReader = null;
		InputStream fiStream = null;
		BufferedReader br = null;
		try {
			File[] tempList = fileDir.listFiles();
			for (int i = 0; i < tempList.length; i++) {
				if (tempList[i].getName().contains(name)&&!tempList[i].getName().contains("汇总")&&!tempList[i].getName().contains("zip")) {
					System.out.println(tempList[i].getName());
					csvName = tempList[i].getName();
				}
			}
			File excel  = new File(ZFB_FILE_PATH + csvName);
			Charset gbk = Charset.forName("gbk");
			//行文件中所有数据
			List<String[]> dataList = new ArrayList<>();
			//暂时存放每一行的数据
			String rowRecord = "";

			fiStream = new FileInputStream(excel); //文件流对象
			inputStreamReader = new InputStreamReader(fiStream, Charset.forName("GBK"));
			br = new BufferedReader(inputStreamReader);
			while ((rowRecord = br.readLine()) != null) {
				String[] lineList = rowRecord.split("\\,");
				if (lineList.length > 4) {
					dataList.add(lineList);
				}
			}
			List<ApiAccount> apiAccountList = new ArrayList<ApiAccount>();
			for (int i = 1; i < dataList.size() ; i++) {
				String[] data = dataList.get(i);
				ApiAccount apiAccount = new ApiAccount();
				if (data[3].contains("易派微生活-游戏充值")) {
					String zfbOrderId = data[0].replace("\t","");   //支付宝交易包
					String userOrderId = data[1].replace("\t","");  //商户订单号
					String businessType = data[2].replace("\t",""); //业务类型
					String goodsName = data[3].replace("\t","");    //商品名称
					String processDate = data[5].replace("\t",""); //订单处理时间
					//String userAccount = data[10].replace("\t","");  //用户账号
					String payAmout = data[11].replace("\t","");  //支付金额
					String txFee = data[22].replace("\t","");    //服务费
					System.out.println(zfbOrderId + ","+ userOrderId + ","+ businessType + "," + goodsName + ","+ processDate + "," + payAmout + "," + txFee );
					//针对数据做处理
				}
			}

		} catch (Exception e) {
			e.printStackTrace();
			logger_paymentcheck.info("读取文件数据并存入数据库出现异常：文件： " + fileName+ "，异常信息：" + e.getMessage());
		} finally {
			closeStream(br, inputStreamReader, fiStream);
		}
	}

	/**
	 * 解析出url参数中的键值对
	 * 如 "index.jsp?Action=del&id=123"，解析出Action:del,id:123存入map中
	 * @param URL url地址
	 * @return url请求参数部分
	 */
	public static Map<String, String> URLRequest(String URL){
		Map<String, String> mapRequest = new HashMap<String, String>();
		String[] arrSplit=null;
		String strUrlParam=TruncateUrlPage(URL);
		if(strUrlParam==null){
			return mapRequest;
		}
		//每个键值为一组
		arrSplit=strUrlParam.split("[&]");
		for(String strSplit:arrSplit){
			String[] arrSplitEqual=null;
			arrSplitEqual= strSplit.split("[=]");
			//解析出键值
			if(arrSplitEqual.length>1){
				//正确解析
				mapRequest.put(arrSplitEqual[0], arrSplitEqual[1]);
			} else {
				if(arrSplitEqual[0]!="") {
					//只有参数没有值，不加入
					mapRequest.put(arrSplitEqual[0], "");
				}
			}
		}
		return mapRequest;
	}

	/**
	 * 去掉url中的路径，留下请求参数部分
	 * @param strURL url地址
	 * @return url请求参数部分
	 */
	private static String TruncateUrlPage(String strURL){
		String strAllParam=null;
		String[] arrSplit=null;
		strURL=strURL.trim().toLowerCase();
		arrSplit=strURL.split("[?]");
		if (strURL.length()>1) {
			if (arrSplit.length>1) {
				if(arrSplit[1]!=null) {
					strAllParam=arrSplit[1];
				}
			}
		}
		return strAllParam;
	}



	/**
	 * 删除文件
	 * @param folderPath 文件夹完整绝对路径
	 */
	public static void delFolder(String folderPath) {
		try {
			delAllFile(folderPath); //删除完里面所有内容
//			String filePath = folderPath;
//			filePath = filePath.toString();
//			java.io.File myFilePath = new java.io.File(filePath);
//			myFilePath.delete(); //删除空文件夹
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

    /**
     * 删除文件夹中的所有文件
     * @param path
     * @return
     */
	public static boolean delAllFile(String path) {
		boolean flag = false;
		File file = new File(path);
		if (!file.exists()) {
			return flag;
		}
		if (!file.isDirectory()) {
			return flag;
		}
		String[] tempList = file.list();
		File temp = null;
		for (int i = 0; i < tempList.length; i++) {
			if (path.endsWith(File.separator)) {
				temp = new File(path + tempList[i]);
			} else {
				temp = new File(path + File.separator + tempList[i]);
			}
			if (temp.isFile()) {
				temp.delete();
			}
			if (temp.isDirectory()) {
				delAllFile(path + "/" + tempList[i]);//先删除文件夹里面的文件
				delFolder(path + "/" + tempList[i]);//再删除空文件夹
				flag = true;
			}
		}
		return flag;
	}


}
