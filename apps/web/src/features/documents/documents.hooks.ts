'use client';

import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { toast } from 'sonner';
import { documentsKeys } from './documents.keys';
import {
  getDocuments,
  getDocument,
  createDocument,
  updateDocument,
  uploadDocumentFile,
  getWorkflowHistory,
  getDocumentAcl,
  addAclEntry,
  authorizeDownload,
  presignDownload,
} from './documents.api';
import type { DocumentListFilters, CreateDocumentDto, UpdateDocumentDto, AddAclEntryDto } from './documents.types';
import { triggerBrowserDownload, revokeObjectUrl } from '@/lib/utils/download';
import { getErrorMessage } from '@/lib/api/errors';
import apiClient from '@/lib/api/client';

// ── Queries ──────────────────────────────────────────────────────────────────

export function useDocumentsList(filters?: DocumentListFilters) {
  return useQuery({
    queryKey: documentsKeys.list(filters),
    queryFn: () => getDocuments(filters),
  });
}

export function useDocumentDetail(id: string) {
  return useQuery({
    queryKey: documentsKeys.detail(id),
    queryFn: () => getDocument(id),
    enabled: Boolean(id),
  });
}

export function useWorkflowHistory(id: string) {
  return useQuery({
    queryKey: documentsKeys.workflowHistory(id),
    queryFn: () => getWorkflowHistory(id),
    enabled: Boolean(id),
  });
}

export function useDocumentAcl(id: string) {
  return useQuery({
    queryKey: documentsKeys.acl(id),
    queryFn: () => getDocumentAcl(id),
    enabled: Boolean(id),
  });
}

// ── Mutations ────────────────────────────────────────────────────────────────

export function useCreateDocument() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (dto: CreateDocumentDto) => createDocument(dto),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: documentsKeys.lists() });
      toast.success('Document created successfully');
    },
    onError: (err) => toast.error(getErrorMessage(err)),
  });
}

export function useUpdateDocument(id: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (dto: UpdateDocumentDto) => updateDocument(id, dto),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: documentsKeys.detail(id) });
      qc.invalidateQueries({ queryKey: documentsKeys.lists() });
      toast.success('Document updated successfully');
    },
    onError: (err) => toast.error(getErrorMessage(err)),
  });
}

export function useUploadDocumentFile(id: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (file: File) => uploadDocumentFile(id, file),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: documentsKeys.detail(id) });
      qc.invalidateQueries({ queryKey: documentsKeys.lists() });
      qc.invalidateQueries({ queryKey: documentsKeys.workflowHistory(id) });
      toast.success('File uploaded successfully');
    },
    onError: (err) => toast.error(getErrorMessage(err)),
  });
}

export function useAddAclEntry(id: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (dto: AddAclEntryDto) => addAclEntry(id, dto),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: documentsKeys.acl(id) });
      qc.invalidateQueries({ queryKey: documentsKeys.detail(id) });
      toast.success('ACL entry added');
    },
    onError: (err) => toast.error(getErrorMessage(err)),
  });
}

export function useDownloadDocument() {
  return useMutation({
    mutationFn: async ({ id, filename }: { id: string; filename?: string }) => {
      const authorization = await authorizeDownload(id);
      const result = await presignDownload(id, authorization.version, authorization.grantToken);
      const resolvedFilename = filename ?? result.filename ?? authorization.filename ?? `document-${id}`;
      const resolvedVersion = result.version ?? authorization.version;

      // Always stream through the gateway. In EKS, MinIO is cluster-internal, so
      // browser-facing presigned URLs like http://minio:9000 are not reachable.
      const streamUrl = `/documents/${id}/versions/${resolvedVersion}/stream?token=${encodeURIComponent(authorization.grantToken)}`;
      const response = await apiClient.get(streamUrl, { responseType: 'blob' });
      const blobUrl = URL.createObjectURL(response.data);
      triggerBrowserDownload(blobUrl, resolvedFilename);
      setTimeout(() => revokeObjectUrl(blobUrl), 5000);
    },
    onError: (err) => toast.error(getErrorMessage(err)),
  });
}
