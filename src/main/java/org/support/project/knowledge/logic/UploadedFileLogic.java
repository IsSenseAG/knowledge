package org.support.project.knowledge.logic;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.fileupload.FileItem;
import org.support.project.aop.Aspect;
import org.support.project.common.log.Log;
import org.support.project.common.log.LogFactory;
import org.support.project.di.Container;
import org.support.project.di.DI;
import org.support.project.di.Instance;
import org.support.project.knowledge.dao.KnowledgeFilesDao;
import org.support.project.knowledge.entity.KnowledgeFilesEntity;
import org.support.project.knowledge.entity.KnowledgesEntity;
import org.support.project.knowledge.vo.UploadFile;
import org.support.project.web.bean.LoginedUser;

@DI(instance=Instance.Singleton)
public class UploadedFileLogic {
	/** ログ */
	private static Log LOG = LogFactory.getLog(UploadedFileLogic.class);

	private KnowledgeFilesDao filesDao = KnowledgeFilesDao.get();
	
	public static UploadedFileLogic get() {
		return Container.getComp(UploadedFileLogic.class);
	}

	/**
	 * ファイルを保存する
	 * @param fileItem
	 * @param loginedUser
	 * @param context
	 * @return
	 * @throws IOException
	 */
	@Aspect(advice=org.support.project.ormapping.transaction.Transaction.class)
	public UploadFile saveFile(FileItem fileItem, LoginedUser loginedUser, String context) throws IOException {
		LOG.trace("saveFile()");
		KnowledgeFilesEntity entity = new KnowledgeFilesEntity();
		entity.setFileName(fileItem.getName());
		entity.setFileSize(new Double(fileItem.getSize()));
		entity.setFileBinary(fileItem.getInputStream());
		entity.setParseStatus(0);
		entity = filesDao.insert(entity);
		UploadFile file = convUploadFile(context, entity);
		//処理が完了したら、テンポラリのファイルを削除
		fileItem.delete();
		return file;
	}
	
	/**
	 * KnowledgeFilesEntity の情報から、画面に戻す UploadFile の情報を生成する
	 * @param context
	 * @param entity
	 * @return
	 */
	private UploadFile convUploadFile(String context, KnowledgeFilesEntity entity) {
		UploadFile file = new UploadFile();
		file.setFileNo(entity.getFileNo());
		file.setUrl(context + "/open.file/download?fileNo=" + entity.getFileNo());
		//file.setThumbnailUrl(context + "/open.file/thumbnai?fileNo=" + entity.getFileNo());
		file.setThumbnailUrl(context + "/bower/teambox.free-file-icons/32px/_blank.png");
		file.setName(entity.getFileName());
		file.setType("-");
		file.setSize(entity.getFileSize());
		file.setDeleteUrl(context + "/protect.file/delete?fileNo=" + entity.getFileNo());
		file.setDeleteType("DELETE");
		if (entity.getKnowledgeId() != null && 0 != entity.getKnowledgeId().longValue()) {
			file.setKnowlegeId(entity.getKnowledgeId());
		}
		return file;
	}

	
	
	/**
	 * ナレッジに紐付く添付ファイルの情報を更新
	 * 
	 * 1. 現在のナレッジに紐づくファイルを一覧取得する
	 * 2. 紐づけるファイルの番号のファイルが存在しない場合、ファイル番号でファイル情報を取得
	 * 2-1. 取得したファイル情報に、ナレッジ番号をセットし更新
	 * 3. 1で取得したファイル一覧から、処理済（紐付け済）ファイル番号を削除する
	 * 4. 1で取得したファイルの一覧で、残っているものがあれば、そのファイルは削除（紐付けがきれている）
	 * 
	 * @param entity
	 * @param fileNos
	 * @param loginedUser
	 */
	@Aspect(advice=org.support.project.ormapping.transaction.Transaction.class)
	public void setKnowledgeFiles(KnowledgesEntity knowledgesEntity, List<Long> fileNos, LoginedUser loginedUser) {
		// 現在、すでに紐づいている添付ファイルを取得
		List<KnowledgeFilesEntity> filesEntities = filesDao.selectOnKnowledgeId(knowledgesEntity.getKnowledgeId());
		Map<Long, KnowledgeFilesEntity> filemap = new HashMap<>();
		for (KnowledgeFilesEntity entity : filesEntities) {
			filemap.put(entity.getFileNo(), entity);
		}
		
		// 画面で設定されている添付ファイルの番号で紐付けを作成
		for (Long fileNo : fileNos) {
			KnowledgeFilesEntity entity = filesDao.selectOnKeyWithoutBinary(fileNo);
			if (entity != null) {
				if (entity.getKnowledgeId() == null || 0 == entity.getKnowledgeId().longValue()) {
					filesDao.connectKnowledge(fileNo, knowledgesEntity.getKnowledgeId(), loginedUser);
				}
			}
			filemap.remove(fileNo);
		}
		
		// 始めに取得した一覧で、紐付けが作成されなかった（＝紐付けが切れた）ファイルを削除
		Iterator<Long> iterator = filemap.keySet().iterator();
		while (iterator.hasNext()) {
			Long fileNo = (Long) iterator.next();
			//filesDao.delete(fileNo);
			filesDao.physicalDelete(fileNo); // バイナリは大きいので、物理削除する
			
			// TODO 全文検索エンジンから情報を削除
			
		}
	}
	
	
	/**
	 * ファイルを削除する
	 * @param fileNo
	 * @param loginedUser 
	 */
	public void removeFile(Long fileNo, LoginedUser loginedUser) {
		// DBのデータを削除
		filesDao.physicalDelete(fileNo); // バイナリは大きいので、物理削除する
		
		// TODO 全文検索エンジンから情報を削除
	}

	/**
	 * 指定のナレッジに紐づく添付ファイルの情報を取得
	 * @param knowledgeId
	 * @param context
	 * @return
	 */
	public List<UploadFile> selectOnKnowledgeId(Long knowledgeId, String context) {
		List<UploadFile> files = new ArrayList<UploadFile>();
		List<KnowledgeFilesEntity> filesEntities = filesDao.selectOnKnowledgeId(knowledgeId);
		for (KnowledgeFilesEntity entity : filesEntities) {
			files.add(convUploadFile(context, entity));
		}
		return files;
	}
	
	
	/**
	 * 指定の添付ファイル番号の情報を取得
	 * @param fileNos
	 * @param context
	 * @return
	 */
	public List<UploadFile> selectOnFileNos(List<Long> fileNos, String context) {
		List<UploadFile> files = new ArrayList<UploadFile>();
		for (Long fileNo : fileNos) {
			KnowledgeFilesEntity entity = filesDao.selectOnKeyWithoutBinary(fileNo);
			files.add(convUploadFile(context, entity));
		}
		return files;
	}

	/**
	 * ナレッジを削除する際に、添付ファイルを削除
	 * @param knowledgeId
	 */
	public void deleteOnKnowledgeId(Long knowledgeId) {
		List<KnowledgeFilesEntity> filesEntities = filesDao.selectOnKnowledgeId(knowledgeId);
		for (KnowledgeFilesEntity entity : filesEntities) {
			filesDao.physicalDelete(entity.getFileNo());
			//TODO 全文検索エンジンからも情報を削除
			
		}
	}
	
	
	/**
	 * ダウンロードする添付ファイルを取得
	 * （取得して良い権限が無い場合はNULLを返す)
	 * @param fileNo
	 * @param loginedUser
	 * @return
	 */
	public KnowledgeFilesEntity getFile(Long fileNo, LoginedUser loginedUser) {
		KnowledgeFilesEntity entity = filesDao.selectOnKeyWithoutBinary(fileNo);
		if (entity == null) {
			return null;
		}
		if (entity.getKnowledgeId() != null && 0 != entity.getKnowledgeId().longValue()) {
			// ナレッジに紐づいている場合、そのナレッジにアクセスできれば添付ファイルにアクセス可能
			KnowledgeLogic knowledgeLogic = KnowledgeLogic.get();
			if (knowledgeLogic.select(entity.getKnowledgeId(), loginedUser) == null) {
				return null;
			}
		} else {
			// ナレッジに紐づいていない場合、登録者のみがアクセス可能
			if (loginedUser == null) {
				return null;
			}
			if (entity.getInsertUser().intValue() != loginedUser.getUserId().intValue()) {
				return null;
			}
		}
		return filesDao.selectOnKey(fileNo);
	}

}
