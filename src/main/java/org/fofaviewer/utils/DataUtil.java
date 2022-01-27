package org.fofaviewer.utils;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.ExcelWriter;
import com.alibaba.excel.write.merge.OnceAbsoluteMergeStrategy;
import com.alibaba.excel.write.metadata.WriteSheet;
import com.alibaba.excel.write.metadata.style.WriteCellStyle;
import com.alibaba.excel.write.metadata.style.WriteFont;
import com.alibaba.excel.write.style.HorizontalCellStyleStrategy;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import javafx.scene.control.*;
import org.fofaviewer.bean.BaseBean;
import org.fofaviewer.bean.ExcelBean;
import org.fofaviewer.bean.TabDataBean;
import org.fofaviewer.bean.TableBean;
import org.tinylog.Logger;
import java.util.*;
import java.util.List;

public class DataUtil {
    private static final RequestUtil helper = RequestUtil.getInstance();
    private static final ResourceBundle resourceBundle = ResourceBundleUtil.getResource();;

    /**
     * 对话框配置
     * @param type dialog type
     * @param header dialog title
     * @param content content of dialog
     */
    public static void showAlert(Alert.AlertType type, String header, String content){
        Alert alert = new Alert(type);
        alert.setTitle("提示");
        alert.setHeaderText(header);
        alert.setContentText(content);
        alert.showAndWait();
    }

    public static void exportToExcel(String fileName, String tabTitle, HashMap<String, ExcelBean> totalData, List<List<String>> urls, StringBuilder errorPage){
        ExcelWriter excelWriter = null;
        try {
            excelWriter = EasyExcel.write(fileName).build();
            OnceAbsoluteMergeStrategy strategy = new OnceAbsoluteMergeStrategy(0,1,0,6);
            ArrayList<ArrayList<String>> head = new ArrayList<ArrayList<String>>(){{add(new ArrayList<String>(){{add(tabTitle);}});}};
            WriteCellStyle contentWriteCellStyle = new WriteCellStyle();
            WriteFont contentWriteFont = new WriteFont();
            contentWriteFont.setFontHeightInPoints((short)16);
            contentWriteCellStyle.setWriteFont(contentWriteFont);
            WriteSheet writeSheet0 = EasyExcel.writerSheet(0, resourceBundle.getString("EXPORT_FILENAME_SHEET1"))
                    .registerWriteHandler(strategy)
                    .registerWriteHandler(new HorizontalCellStyleStrategy(null, contentWriteCellStyle)).build();
            excelWriter.write(head, writeSheet0);
            WriteSheet writeSheet1 = EasyExcel.writerSheet(1, resourceBundle.getString("EXPORT_FILENAME_SHEET2"))
                    .head(ExcelBean.class).build();
            excelWriter.write(new ArrayList<>(totalData.values()), writeSheet1);
            WriteSheet writeSheet2 = EasyExcel.writerSheet(2, resourceBundle.getString("EXPORT_FILENAME_SHEET3")).build();
            excelWriter.write(urls, writeSheet2);
            if(errorPage.length() == 0){
                showAlert(Alert.AlertType.INFORMATION, null, resourceBundle.getString("EXPORT_MESSAGE1") + fileName);
            }else{
                showAlert(Alert.AlertType.INFORMATION, null, resourceBundle.getString("EXPORT_MESSAGE2_1")
                        + errorPage.toString() + resourceBundle.getString("EXPORT_MESSAGE2_2") + " " + fileName);
            }
        }catch(Exception exception){
            Logger.error(exception);
            showAlert(Alert.AlertType.INFORMATION, null, resourceBundle.getString("EXPORT_ERROR"));
        }finally {
            if (excelWriter != null) {
                excelWriter.finish();
            }
        }
    }

    public static Map<String, ? extends BaseBean> loadJsonData(TabDataBean bean,
                                                               JSONObject obj, HashMap<String, ExcelBean> excelData,
                                                               HashMap<String,String> urlList,
                                                               boolean isExport,
                                                               TableView<TableBean> view){
        JSONArray array = obj.getJSONArray("results");
        HashMap<String, TableBean> list = new HashMap<>();
        for(int index=0; index < array.size(); index ++){
            JSONArray _array = array.getJSONArray(index);
            String host = _array.getString(0);
            int port = Integer.parseInt(_array.getString(4));
            String ip = _array.getString(2);
            String _host = ip + ":" + port;
            if((port==443 && host.equals("https://"+ _host) && list.containsKey("https://"+ip)) || (port == 80 && host.equals(_host) && list.containsKey(ip))){
                continue;
            }

            String title = _array.getString(1);
            String domain = _array.getString(3);
            String protocol = _array.getString(5);
            String server = _array.getString(6);
            String cert = _array.getString(7);
            String certCN = "";
            String fid;
            try{
                fid =  _array.getString(8);
            }catch(IndexOutOfBoundsException e){
                fid = "";
            }
            if(!cert.isEmpty()){
                certCN = helper.getCertSubjectDomainByFoFa(cert);
                cert = helper.getCertSerialNumberByFoFa(cert);
            }
            if(isExport){ // 是否为导出数据
                ExcelBean d = new ExcelBean(host, title, ip, domain, port, protocol, server, fid, certCN);
                //去除443 的重复项
                if(port==443 && excelData.containsKey("https://"+_host)){
                    excelData.remove("https://"+_host);
                }
                // 去除80端口的重复项
                if(port==80 && excelData.containsKey(_host)){
                    excelData.remove(_host);
                }
                getUrlList(urlList, host, ip, port, protocol, _host);
                excelData.put(host, d);
            }else{  // table 页更新数据
                TableBean b = new TableBean(0, host, title, ip, domain, port, protocol, server, fid, cert, certCN);
                //去除443 的重复项
                if(port==443 && list.containsKey("https://"+_host)){
                    b.num = list.get("https://"+_host).num;
                    list.remove("https://"+_host);
                }
                // 去除80端口的重复项
                if(port==80 && list.containsKey(_host)){
                    b.num = list.get(_host).num;
                    list.remove(_host);
                }
                if(b.num.getValue() == 0){ b.num.set(++bean.count);}
                getUrlList(bean.dataList, host, ip, port, protocol, _host);
                list.put(host, b);
            }
        }
        try {
            CertRequestUtil util = new CertRequestUtil(view);
            if (isExport) {
                util.getCertDomain(excelData, true);
            } else {
                util.getCertDomain(list, false);
            }
        }catch (InterruptedException e){
            Logger.error(e);
        }
        return list;
    }

    public static void getUrlList(HashMap<String, String> urlList, String host, String ip, int port, String protocol, String _host) {
        if (protocol.isEmpty() && host.endsWith("443") && !host.startsWith("http")){
            urlList.put("https://" + host, "https://" + host);
        }else if(port == 80 && protocol.equals("http")){
            urlList.put("http://" + ip, "http://" + ip);
        } else if (protocol.equals("http") && !urlList.containsKey(_host)) {
            urlList.put("http://" + host, "http://" + host);
        } else if (port == 443 && protocol.equals("https") && !urlList.containsKey("https://" + ip)) {
            urlList.put("https://" + ip, "https://" + ip);
        } else if (port != 443 && protocol.equals("https") && !urlList.containsKey("https://" + _host)) {
            urlList.put("https://" + _host, "https://" + _host);
        }
    }

    /**
     * 将IP地址转换为浮点数
     * @param ip IP
     * @return double value
     */
    public static Double getValueFromIP(String ip){
        String[] str = ip.split("\\.");
        return Double.parseDouble(str[0]) * 1000000 + Double.parseDouble(str[1]) * 1000
                + Double.parseDouble(str[2]) + Double.parseDouble(str[3]) * 0.001;
    }

    public static String replaceString(String tabTitle){
        if(tabTitle.startsWith("(*)")){
            tabTitle = tabTitle.substring(3);
            tabTitle = "(" + tabTitle + ") && (is_honeypot=false && is_fraud=false)";
        }
        return tabTitle;
    }
}
