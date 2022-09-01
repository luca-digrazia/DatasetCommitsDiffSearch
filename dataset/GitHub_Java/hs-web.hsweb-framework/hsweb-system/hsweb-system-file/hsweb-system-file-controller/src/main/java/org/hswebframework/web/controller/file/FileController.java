package org.hswebframework.web.controller.file;

import com.alibaba.fastjson.JSON;
import org.hswebframework.expands.compress.Compress;
import org.hswebframework.expands.compress.zip.ZIPWriter;
import org.hswebframework.utils.StringUtils;
import org.hswebframework.web.NotFoundException;
import org.hswebframework.web.authorization.Authentication;
import org.hswebframework.web.authorization.annotation.Authorize;
import org.hswebframework.web.controller.message.ResponseMessage;
import org.hswebframework.web.entity.file.FileInfoEntity;
import org.hswebframework.web.logging.AccessLogger;
import org.hswebframework.web.service.file.FileInfoService;
import org.hswebframework.web.service.file.FileService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.activation.MimetypesFileTypeMap;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * TODO 完成注释
 *
 * @author zhouhao
 */
@RestController
@RequestMapping("${hsweb.web.mappings.file:file}")
@Authorize(permission = "file")
@AccessLogger("文件")
public class FileController {

    private String staticFilePath = "./static";

    private String staticLocation = "/";

    @Value("${hsweb.web.upload.staticFilePath:./static}")
    public void setStaticFilePath(String staticFilePath) {
        this.staticFilePath = staticFilePath;
    }

    @Value("${hsweb.web.upload.staticLocation:/}")
    public void setStaticLocation(String staticLocation) {
        this.staticLocation = staticLocation;
    }

    private FileService fileService;

    private FileInfoService fileInfoService;

    private Logger logger = LoggerFactory.getLogger(this.getClass());

    private static final Pattern fileNameKeyWordPattern = Pattern.compile("(\\\\)|(/)|(:)(|)|(\\?)|(>)|(<)|(\")");

    @Autowired
    public void setFileService(FileService fileService) {
        this.fileService = fileService;
    }

    @Autowired
    public void setFileInfoService(FileInfoService fileInfoService) {
        this.fileInfoService = fileInfoService;
    }

    /**
     * 构建并下载zip文件.仅支持POST请求
     *
     * @param name     文件名
     * @param dataStr  数据,jsonArray. 格式:[{"name":"fileName","text":"fileText"}]
     * @param response {@link HttpServletResponse}
     * @throws IOException      写出zip文件错误
     * @throws RuntimeException 构建zip文件错误
     */
    @RequestMapping(value = "/download-zip/{name:.+}", method = {RequestMethod.POST})
    @AccessLogger("下载zip文件")
    @Authorize(action = "download")
    public void downloadZip(@PathVariable("name") String name,
                            @RequestParam("data") String dataStr,
                            HttpServletResponse response) throws IOException {
        response.setContentType(MediaType.APPLICATION_OCTET_STREAM_VALUE);
        response.setHeader("Content-disposition", "attachment;filename=" + URLEncoder.encode(name, "utf-8"));
        ZIPWriter writer = Compress.zip();
        List<Map<String, String>> data = (List) JSON.parseArray(dataStr, Map.class);
        data.forEach(map -> writer.addTextFile(map.get("name"), map.get("text")));
        writer.write(response.getOutputStream());
    }

    /**
     * 构建一个文本文件,并下载.支持GET,POST请求
     *
     * @param name     文件名
     * @param text     文本内容
     * @param response {@link HttpServletResponse}
     * @throws IOException 写出文本内容错误
     */
    @RequestMapping(value = "/download-text/{name:.+}", method = {RequestMethod.GET, RequestMethod.POST})
    @AccessLogger("下载text文件")
    @Authorize(action = "download")
    public void downloadTxt(@PathVariable("name") String name,
                            @RequestParam("text") String text,
                            HttpServletResponse response) throws IOException {
        response.setContentType(MediaType.APPLICATION_OCTET_STREAM_VALUE);
        response.setHeader("Content-disposition", "attachment;filename=" + URLEncoder.encode(name, "utf-8"));
        response.getWriter().write(text);
    }

    /**
     * 使用restful风格,通过文件ID下载已经上传的文件,支持断点下载
     * 如: http://host:port/file/download/aSk2a/file.zip 将下载 ID为aSk2a的文件.并命名为file.zip
     *
     * @param id       文件ID
     * @param name     文件名
     * @param response {@link HttpServletResponse}
     * @param request  {@link HttpServletRequest}
     * @return 下载结果, 在下载失败时, 将返回错误信息
     * @throws IOException       读写文件错误
     * @throws NotFoundException 文件不存在
     */
    @RequestMapping(value = "/download/{id}/{name:.+}", method = RequestMethod.GET)
    @AccessLogger("下载文件")
    @Authorize(action = "download")
    public void restDownLoad(@PathVariable("id") String id,
                             @PathVariable("name") String name,
                             HttpServletResponse response,
                             HttpServletRequest request) throws IOException {
        downLoad(id, name, response, request);
    }

    /**
     * 通过文件ID下载已经上传的文件,支持断点下载
     * 如: http://host:port/file/download/aSk2a/file.zip 将下载 ID为aSk2a的文件.并命名为file.zip
     *
     * @param id       要下载资源文件的id
     * @param name     自定义文件名，该文件名不能存在非法字符.如果此参数为空(null).将使用文件上传时的文件名
     * @param response {@link javax.servlet.http.HttpServletResponse}
     * @param request  {@link javax.servlet.http.HttpServletRequest}
     * @return 下载结果, 在下载失败时, 将返回错误信息
     * @throws IOException                              读写文件错误
     * @throws org.hswebframework.web.NotFoundException 文件不存在
     */
    @GetMapping(value = "/download/{id}")
    @AccessLogger("下载文件")
    @Authorize(action = "download")
    public void downLoad(@PathVariable("id") String id,
                         @RequestParam(value = "name", required = false) String name,
                         HttpServletResponse response, HttpServletRequest request)
            throws IOException {
        FileInfoEntity fileInfo = fileInfoService.selectByPk(id);
        if (fileInfo == null || fileInfo.getStatus() != 1) {
            throw new NotFoundException("文件不存在");
        }
        String fileName = fileInfo.getName();

        String suffix = fileName.contains(".") ?
                fileName.substring(fileName.lastIndexOf("."), fileName.length()) :
                "";
        //获取contentType
        String contentType = fileInfo.getType() == null ?
                MimetypesFileTypeMap.getDefaultFileTypeMap().getContentType(fileName) :
                fileInfo.getType();
        //未自定义文件名，则使用上传时的文件名
        if (StringUtils.isNullOrEmpty(name))
            name = fileInfo.getName();
        //如果未指定文件拓展名，则追加默认的文件拓展名
        if (!name.contains("."))
            name = name.concat(".").concat(suffix);
        //关键字剔除
        name = fileNameKeyWordPattern.matcher(name).replaceAll("");
        int skip = 0;
        long fSize = fileInfo.getSize();
        //尝试判断是否为断点下载
        try {
            //获取要继续下载的位置
            String Range = request.getHeader("Range").replaceAll("bytes=", "").replaceAll("-", "");
            skip = StringUtils.toInt(Range);
        } catch (Exception e) {
        }
        response.setContentLength((int) fSize);//文件大小
        response.setContentType(contentType);
        response.setHeader("Content-disposition", "attachment;filename=" + URLEncoder.encode(name, "utf-8"));
        //断点下载
        if (skip > 0) {
            response.setStatus(HttpServletResponse.SC_PARTIAL_CONTENT);
            String contentRange = "bytes " + skip + "-" + (fSize - 1) + "/" + fSize;
            response.setHeader("Content-Range", contentRange);
        }
        fileService.writeFile(id, response.getOutputStream(), skip);
    }

    /**
     * 上传文件,支持多文件上传.获取到文件流后,调用{@link org.hswebframework.web.service.file.FileService#saveFile(InputStream, String, String, String)}进行文件保存
     * 上传成功后,将返回资源信息如:[{"id":"fileId","name":"fileName","md5":"md5"}]
     *
     * @param files 文件列表
     * @return 文件上传结果.
     * @throws IOException 保存文件错误
     */
    @PostMapping(value = "/upload")
    @AccessLogger("上传文件")
    @Authorize(action = "upload")
    public ResponseMessage<List<FileInfoEntity>> upload(MultipartFile[] files) throws IOException {
        if (logger.isInfoEnabled())
            logger.info(String.format("start upload , file number:%s", files.length));
        List<FileInfoEntity> resourcesList = new LinkedList<>();
        Authentication authentication = Authentication.current().orElseThrow(null);
        String creator = authentication == null ? null : authentication.getUser().getId();
        for (MultipartFile file : files) {
            if (!file.isEmpty()) {
                if (logger.isInfoEnabled())
                    logger.info("start write file:{}", file.getOriginalFilename());
                String fileName = file.getOriginalFilename();
                FileInfoEntity resources = fileService.saveFile(file.getInputStream(), fileName, file.getContentType(), creator);
                resourcesList.add(resources);
            }
        }//响应上传成功的资源信息
        return ResponseMessage.ok(resourcesList)
                .include(FileInfoEntity.class, FileInfoEntity.id, FileInfoEntity.name, FileInfoEntity.id);
    }

    @PostMapping(value = "/upload-static")
    @AccessLogger("上传静态文件")
    @Authorize(action = "static")
    public ResponseMessage<String> uploadStatic(MultipartFile file) throws IOException {
        if (file.isEmpty()) return ResponseMessage.ok();
        return ResponseMessage.ok(fileService.saveStaticFile(file.getInputStream(), file.getOriginalFilename()));
    }
}
