'use client';

import { useState } from 'react';
import apiClient from '@/lib/api/client';
import { apiEndpoints } from '@/lib/api/endpoints';
import { getErrorMessage } from '@/lib/api/errors';

interface UseDocumentPreviewOptions {
  onError?: (message: string) => void;
}

function buildPreviewPath(docId: string, version?: number): string {
  const endpoint = apiEndpoints.documents.preview(docId);
  return `${endpoint}${version !== undefined ? `?version=${version}` : ''}`;
}

export function useDocumentPreview(options?: UseDocumentPreviewOptions) {
  const [isLoading, setIsLoading] = useState(false);

  /**
   * Fetches image binary via axios (with auth token) and returns a blob URL.
   * Ensures the browser receives the actual binary image data, not a redirect HTML page.
   */
  async function getImageUrl(
    docId: string,
    version?: number,
  ): Promise<string> {
    setIsLoading(true);
    try {
      const response = await apiClient.get(buildPreviewPath(docId, version), {
        responseType: 'arraybuffer',
      });

      // Determine content type from response headers or fall back to image/png
      const contentType = (response.headers['content-type'] as string | undefined)
        ?.split(';')[0]
        ?.trim()
        ?? 'image/png';

      const blob = new Blob([response.data as ArrayBuffer], { type: contentType });
      return URL.createObjectURL(blob);
    } catch (err) {
      options?.onError?.(
        err instanceof Error ? err.message : 'Failed to load preview image',
      );
      throw err;
    } finally {
      setIsLoading(false);
    }
  }

  /**
   * Fetches PDF data via axios (with auth token) and returns ArrayBuffer for pdfjs.
   * pdfjs is passed ArrayBuffer directly to avoid auth issues with its internal fetch.
   */
  async function getPdfData(
    docId: string,
    version?: number,
  ): Promise<{ data: ArrayBuffer; filename: string }> {
    setIsLoading(true);
    try {
      const response = await apiClient.get(buildPreviewPath(docId, version), {
        responseType: 'arraybuffer',
      }).catch((err: unknown) => {
        let bodyStr = '';
        const e = err as { details?: { data?: Uint8Array }; statusCode?: number; message?: string };
        if (e.details?.data) {
          try {
            bodyStr = new TextDecoder().decode(e.details.data);
          } catch { bodyStr = 'cannot decode'; }
        }
        console.error('[Preview] API error:', e.statusCode, e.message, 'BODY:', bodyStr);
        throw err;
      });

      // Extract filename from Content-Disposition header if present
      const disposition = response.headers['content-disposition'];
      let filename = 'document.pdf';
      if (disposition) {
        const match = disposition.match(/filename="?([^";]+)"?/);
        if (match) filename = decodeURIComponent(match[1]);
      }

      return { data: response.data as ArrayBuffer, filename };
    } catch (err) {
      options?.onError?.(getErrorMessage(err));
      throw err;
    } finally {
      setIsLoading(false);
    }
  }

  return { getImageUrl, getPdfData, isLoading };
}
