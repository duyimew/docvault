'use client';

import { useState } from 'react';
import { authorizeDownload, presignDownload } from '@/features/documents/documents.api';
import apiClient from '@/lib/api/client';
import { getErrorMessage } from '@/lib/api/errors';
import { triggerBrowserDownload, revokeObjectUrl } from '@/lib/utils/download';

interface UseDownloadDocumentOptions {
  onError?: (message: string) => void;
}

export function useDownloadDocument(options?: UseDownloadDocumentOptions) {
  const [isDownloading, setIsDownloading] = useState(false);

  async function download(docId: string) {
    setIsDownloading(true);
    try {
      // 1. Authorize — metadata-service checks ACL/classification/role
      const authorization = await authorizeDownload(docId);
      const filename = authorization.filename || `document-${docId}`;

      // 2. Presign URL — pass grantToken so document-service skips re-authorization
      const result = await presignDownload(docId, authorization.version, authorization.grantToken);
      const resolvedVersion = result.version ?? authorization.version;
      const resolvedFilename = result.filename || filename;

      // Always stream through the gateway. In EKS, MinIO is cluster-internal, so
      // browser-facing presigned URLs like http://minio:9000 are not reachable.
      const streamUrl = `/documents/${docId}/versions/${resolvedVersion}/stream?token=${encodeURIComponent(authorization.grantToken)}`;
      const response = await apiClient.get(streamUrl, { responseType: 'blob' });

      const blobUrl = URL.createObjectURL(response.data);
      triggerBrowserDownload(blobUrl, resolvedFilename);
      // Free memory after a short delay to allow download to start
      setTimeout(() => revokeObjectUrl(blobUrl), 5000);
    } catch (err) {
      options?.onError?.(getErrorMessage(err));
    } finally {
      setIsDownloading(false);
    }
  }

  return { download, isDownloading };
}
